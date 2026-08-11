import sys
sys.stdout.reconfigure(encoding="utf-8")

import os
import re
import json
import time
from collections import defaultdict, deque

from fastapi import FastAPI, Depends, Header, HTTPException
from pydantic import BaseModel, Field
from anthropic import Anthropic
from dotenv import load_dotenv

import firebase_admin
from firebase_admin import credentials, auth as fb_auth

load_dotenv()

app = FastAPI(
    title="GlycoAI API",
    version="1.3"
)

client = Anthropic(
    api_key=os.getenv("ANTHROPIC_API_KEY")
)


# =====================================================
# Authentication
# =====================================================
#
# REQUIRE_AUTH exists for staged rollout. Deploy this backend with it OFF,
# ship the app update that sends the token, confirm traffic is authenticated,
# THEN set it to true. Turning it on first breaks every installed copy.

REQUIRE_AUTH = os.getenv("REQUIRE_AUTH", "false").strip().lower() == "true"

_firebase_ready = False
_service_account = os.getenv("FIREBASE_SERVICE_ACCOUNT")

if _service_account:
    try:
        _cred = credentials.Certificate(json.loads(_service_account))
        firebase_admin.initialize_app(_cred)
        _firebase_ready = True
        print(" Firebase Admin initialized")
    except Exception as _e:
        print(" FIREBASE INIT FAILED:", _e)
else:
    print(" FIREBASE_SERVICE_ACCOUNT not set - auth cannot be enforced")

if REQUIRE_AUTH and not _firebase_ready:
    print(" WARNING: REQUIRE_AUTH is on but Firebase is not ready. "
          "All requests will be rejected.")
elif not REQUIRE_AUTH:
    print(" WARNING: REQUIRE_AUTH is off - this API is OPEN TO THE PUBLIC")


async def current_uid(authorization: str | None = Header(default=None)) -> str:
    """Verify the Firebase ID token and return the caller's UID."""

    if not REQUIRE_AUTH:
        return "anonymous"

    if not _firebase_ready:
        raise HTTPException(status_code=503, detail="Authentication unavailable")

    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Sign in required")

    token = authorization.split(" ", 1)[1].strip()

    try:
        decoded = fb_auth.verify_id_token(token)
    except Exception:
        # Deliberately vague: never tell a caller why a token failed.
        raise HTTPException(status_code=401, detail="Session expired")

    uid = decoded.get("uid")
    if not uid:
        raise HTTPException(status_code=401, detail="Session expired")
    return uid


# =====================================================
# Rate limiting
# =====================================================
#
# In-memory sliding window, keyed by Firebase UID. Good enough for a single
# Railway instance. If you ever scale to more than one, move this to Redis -
# each instance keeps its own counters, so the effective limit multiplies.

RATE_PER_MIN = int(os.getenv("RATE_PER_MIN", "20"))
RATE_PER_DAY = int(os.getenv("RATE_PER_DAY", "300"))

_hits: dict[str, deque] = defaultdict(deque)

# Roughly 7 MB of base64 - large enough for a phone photo, small enough that
# nobody can use the endpoint as free storage or run up the bill.
MAX_ATTACHMENT_CHARS = 7_000_000


def enforce_rate_limit(uid: str) -> None:
    if not REQUIRE_AUTH:
        return

    now = time.time()
    window = _hits[uid]

    while window and now - window[0] > 86_400:
        window.popleft()

    recent = sum(1 for t in window if now - t < 60)
    if recent >= RATE_PER_MIN:
        raise HTTPException(
            status_code=429,
            detail="Too many messages just now. Please wait a moment."
        )

    if len(window) >= RATE_PER_DAY:
        raise HTTPException(
            status_code=429,
            detail="Daily limit reached. Please try again tomorrow."
        )

    window.append(now)

    # Keep the dict from growing without bound on a long-lived process.
    if len(_hits) > 10_000:
        for k in [k for k, v in _hits.items() if not v]:
            _hits.pop(k, None)


def check_attachment_size(request) -> None:
    for blob in (request.image_data, request.document_data):
        if blob and len(blob) > MAX_ATTACHMENT_CHARS:
            raise HTTPException(
                status_code=413,
                detail="That file is too large. Please try a smaller one."
            )


print("===================================")
print(" GlycoAI Backend Started")
print(" Claude Connected")
print("===================================")


# =====================================================
# Patient Profile
# =====================================================

class ProfileData(BaseModel):

    name: str

    age: int

    sex: str = "Unknown"

    country: str = "Pakistan"

    diabetes_type: str = "unknown"

    diagnosis_year: str | None = None

    insulin_type: str | None = None

    medications: list[str] = Field(default_factory=list)

    glucose_monitoring: str | None = None

    severe_hypoglycemia: str | None = None

    other_conditions: list[str] = Field(default_factory=list)

    hba1c: float | None = None

    complications: list[str] = Field(default_factory=list)

    language: str = "en"

    response_style: str = "simple"

    glucose_unit: str = "mg/dL"

    known_facts: list[str] = Field(default_factory=list)

    weight_kg: float | None = None

    height_cm: float | None = None

    smoking_status: str = ""

    purpose: str = "patient"

    glucose_summary: str | None = None

    # Allergies are safety-critical and never filtered out of the prompt.
    # hba1c_date lets the model treat a stale HbA1c as stale.
    allergies: list[str] = Field(default_factory=list)
    hba1c_date: str | None = None


# =====================================================
# Incoming Chat Request
# =====================================================

class ChatRequest(BaseModel):

    message: str

    profile: ProfileData

    conversation_history: list[dict] = Field(default_factory=list)

    image_data: str | None = None

    image_type: str | None = None

    document_data: str | None = None

    document_type: str | None = None

    document_name: str | None = None


# =====================================================
# Emergency Detection
# =====================================================

# =====================================================
# Text normalisation for safety matching
# =====================================================
#
# Urdu makes naive string matching unreliable:
#   - Urdu-Indic digits (۰۱۲۳) and Arabic-Indic digits (٠١٢٣) are not ASCII,
#     so "شوگر ۴۵" contains no digit a plain regex can see
#   - the same word has several valid spellings (بےہوش / بے ہوش / بیہوش)
#   - zero-width joiners are invisible but break equality
#   - Arabic and Urdu letter variants (ي vs ی, ك vs ک) look identical
#
# Everything is normalised BEFORE matching. This is the single biggest
# accuracy gain here - much larger than adding more keywords.

_DIGIT_MAP = {}
for _i in range(10):
    _DIGIT_MAP[ord("۰") + _i] = str(_i)   # Urdu-Indic  U+06F0..U+06F9
    _DIGIT_MAP[ord("٠") + _i] = str(_i)   # Arabic-Indic U+0660..U+0669

# Zero-width non-joiner, zero-width joiner, tatweel, and Arabic diacritics.
_STRIP_CHARS = dict.fromkeys(
    [0x200C, 0x200D, 0x200E, 0x200F, 0x0640, 0x0670]
    + list(range(0x064B, 0x0660))
)

# Letter variants that render alike but are distinct code points.
_LETTER_MAP = {
    ord("ي"): "ی", ord("ى"): "ی",
    ord("ك"): "ک",
    ord("ه"): "ہ", ord("ۂ"): "ہ", ord("ة"): "ہ",
    ord("أ"): "ا", ord("إ"): "ا", ord("آ"): "ا", ord("ٱ"): "ا",
    ord("ؤ"): "و",
    ord("ئ"): "ی",
}


def normalize_for_safety(text: str) -> str:
    """Fold a message into a form that keyword matching can rely on."""
    if not text:
        return ""
    t = text.translate(_DIGIT_MAP).translate(_STRIP_CHARS).translate(_LETTER_MAP)
    t = t.lower()
    t = re.sub(r"\s+", " ", t)
    return t.strip()


def _spaceless(text: str) -> str:
    """Space-free copy, so 'بے ہوش' and 'بےہوش' both match one entry."""
    return re.sub(r"\s+", "", text)


# =====================================================
# Emergency Detection
# =====================================================
#
# URDU TERMS BELOW ARE GLOSSED IN ENGLISH - PLEASE HAVE A NATIVE SPEAKER
# VERIFY THEM. A wrong term here means a real emergency is missed.

EMERGENCY_WORDS = [
    # --- English ---
    "unconscious", "passed out", "not breathing", "can't breathe",
    "cant breathe", "difficulty breathing", "struggling to breathe",
    "seizure", "fitting", "convulsion", "stroke", "heart attack",
    "chest pain", "collapse", "collapsed", "unresponsive",
    "won't wake", "wont wake", "not waking",

    # --- Roman Urdu (spelling varies a lot in practice) ---
    "behosh", "behoash", "bayhosh", "be hosh", "behoshi",
    "hosh nahi", "hosh nhi", "hosh me nahi",
    "saans nahi", "sans nahi", "saans nhi", "saans nahin",
    "saans lene me", "sans lene me",
    "dora par", "daura par", "dorra", "mirgi",
    "jhatke", "jhatkay",
    "seene me dard", "seene mein dard", "chaati me dard",
    "dil ka dora", "dil ka daura",
    "falij", "faalij",
    "gir gaya", "gir gai", "gir gayi",
    "jaag nahi", "jaag nhi", "uth nahi raha",
    "hospital le ja", "ambulance",

    # --- Urdu script ---
    "بیہوش",          # unconscious
    "بےہوش",          # unconscious (yeh barree spelling)
    "بہوش",           # unconscious (common typo form)
    "ہوش نہیں",       # not conscious
    "ہوش میں نہیں",   # not in consciousness
    "سانس نہیں",      # not breathing
    "سانس لینے میں",  # difficulty in breathing
    "دورہ پڑ",        # a fit/seizure struck (collocation avoids "دورہ" = visit)
    "مرگی",           # epilepsy / fit
    "جھٹکے",          # convulsions
    "سینے میں درد",   # chest pain
    "چھاتی میں درد",  # chest pain (alt)
    "دل کا دورہ",     # heart attack
    "فالج",           # stroke / paralysis
    "گر گیا",         # collapsed / fell
    "گر گئی",         # collapsed / fell (feminine)
    "جاگ نہیں",       # not waking
    "اٹھ نہیں رہا",   # not getting up
    "ایمبولینس",      # ambulance
    "ہسپتال لے جا",   # take to hospital
]

DOSING_WORDS = [
    # --- English ---
    "how many units", "how much insulin", "increase insulin",
    "reduce insulin", "insulin dose", "change insulin", "adjust insulin",
    "stop taking", "should i stop", "double my dose", "skip my dose",
    "how much metformin", "increase my dose", "decrease my dose",

    # --- Roman Urdu ---
    "dose bata", "dose batao", "kitni insulin", "insulin kitni",
    "kitni units", "kitna dose", "dawa band", "dawai band",

    # --- Urdu script ---
    "کتنی انسولین",   # how much insulin
    "انسولین کتنی",   # insulin how much
    "کتنے یونٹ",      # how many units
    "خوراک بتا",      # tell me the dose
    "ڈوز بتا",        # tell me the dose (borrowed)
    "دوا بند",        # stop the medicine
    "دوائی بند",      # stop the medicine
    "دوا بڑھا",       # increase the medicine
    "دوا کم",         # reduce the medicine
]

# Pre-compute space-free forms once, so both spacing styles match.
_EMERGENCY_NORM = [(normalize_for_safety(w), _spaceless(normalize_for_safety(w)))
                   for w in EMERGENCY_WORDS]
_DOSING_NORM = [(normalize_for_safety(w), _spaceless(normalize_for_safety(w)))
                for w in DOSING_WORDS]

# =====================================================
# Centralised glucose thresholds
# =====================================================
# Single source of truth for the backend. Mirrors isUrgentReading() and
# suggestionFor() in Glucosesuggestion.kt so the Tracker tab and the chat can
# never disagree about the same reading. A clinician should review these.
#
# mg/dL is canonical; mmol/L is converted before comparison.
GLUCOSE_SEVERE_LOW_MGDL = 54
GLUCOSE_LOW_MGDL = 70
GLUCOSE_HIGH_MGDL = 400

# Symptoms that turn a borderline number into an urgent one. A reading of 65
# alone is a hypo to treat at home; 65 with confusion is not.
SEVERE_SYMPTOM_WORDS = [
    # English
    "confus", "can't think", "cant think", "disoriented", "slurring",
    "slurred", "shaking", "sweating heavily", "cold sweat", "vomit",
    "throwing up", "can't wake", "cant wake", "unconscious", "passed out",
    "not breathing", "difficulty breathing", "seizure", "chest pain",
    "collapse", "dizzy", "dizziness", "lightheaded", "light-headed",
    "faint", "fainting", "blurred vision", "very weak", "can't stand",
    # Roman Urdu
    "chakkar", "ghabrahat", "pasina", "ulti", "kamzori", "behosh",
    "nazar dhundli",
    # Urdu script
    "چکر",          # dizziness
    "الٹی",         # vomiting
    "پسینہ",        # sweating
    "کمزوری",       # weakness
    "گھبراہٹ",      # palpitations / anxiety
    "دھندلا",       # blurred
    "ہوش نہیں",     # not conscious
    "کانپ",         # shaking / trembling
]


# Glucose values, matched as whole numbers so "sugar 300" is never mistaken
# for "sugar 30". Urdu terms included; digits are already normalised to ASCII
# by the time this runs.
GLUCOSE_PATTERN = re.compile(
    r"(?:sugar|glucose|bsl|reading|shugar|sheeta|"
    r"شوگر|گلوکوز|شکر\s*لیول|ریڈنگ)"
    r"\D{0,14}(\d{1,4})(?!\d)",
    re.IGNORECASE | re.UNICODE,
)

# These bypass the model entirely, so each must carry its own translation -
# otherwise an Urdu-speaking user gets English-only instructions at the exact
# moment comprehension matters most. Both languages are always shown: at this
# point the priority is that SOMEONE nearby can read it.

EMERGENCY_RESPONSE = (
    "This may be a medical emergency. Call 1122 now, or go to the nearest "
    "hospital immediately.\n\n"
    "If the person is awake and able to swallow, and their sugar is low, give "
    "them something sweet right away - juice, a regular soft drink, or sugar "
    "in water. Do NOT put anything in their mouth if they are drowsy or "
    "unconscious.\n\n"
    "یہ ہنگامی طبی صورتحال ہو سکتی ہے۔ ابھی 1122 پر کال کریں یا قریبی ہسپتال "
    "لے جائیں۔\n\n"
    "اگر مریض ہوش میں ہے اور نگل سکتا ہے، اور شوگر کم ہے، تو فوراً کچھ میٹھا "
    "دیں - جوس، عام مشروب، یا پانی میں چینی۔ اگر وہ غنودگی میں ہے یا بےہوش ہے "
    "تو منہ میں کچھ نہ ڈالیں۔"
)

# Unlike the emergency text, this is shown in ONE language only. The emergency
# response is bilingual because whoever is holding the phone may not be the
# patient, and comprehension matters more than length. A dosing refusal has no
# such urgency - doubling its length just makes it harder to read.
DOSING_RESPONSE_EN = (
    "I can't work out insulin or medicine doses - that has to come from your "
    "own doctor, because it depends on tests and details only they can see.\n\n"
    "Please follow the instructions you were given, and contact your doctor if "
    "you think something needs to change. If you feel unwell right now and are "
    "unsure what to do, treat it as urgent and seek care."
)

DOSING_RESPONSE_UR = (
    "میں انسولین یا دوا کی مقدار نہیں بتا سکتا - یہ صرف آپ کا ڈاکٹر ہی طے کر "
    "سکتا ہے، کیونکہ اس کا انحصار ان ٹیسٹوں اور تفصیلات پر ہے جو صرف وہ "
    "دیکھ سکتے ہیں۔\n\n"
    "آپ کو جو ہدایات دی گئی ہیں ان پر عمل کریں، اور اگر آپ کو لگتا ہے کہ کچھ "
    "بدلنے کی ضرورت ہے تو اپنے ڈاکٹر سے رابطہ کریں۔ اگر ابھی طبیعت ٹھیک نہیں "
    "اور سمجھ نہ آ رہا ہو کہ کیا کریں، تو اسے فوری سمجھیں اور مدد لیں۔"
)

def check_safety(message: str, glucose_unit: str = "mg/dL", language: str = "en"):
    """Hard safety gate that runs before the model sees anything.

    This is a FAST PATH, not the only line of defence. Keyword matching cannot
    cover every way a person might describe an emergency, especially across two
    languages and two scripts. The system prompt makes the model responsible for
    catching what this misses, and parse_model_reply enforces that an emergency
    reply never asks a clarifying question.
    """

    norm = normalize_for_safety(message)
    norm_nospace = _spaceless(norm)

    for word, word_nospace in _EMERGENCY_NORM:
        if word and (word in norm or word_nospace in norm_nospace):
            return {"blocked": True, "response": EMERGENCY_RESPONSE, "tier": "emergency"}

    # Computed once: used to escalate borderline readings below.
    has_symptom = any(
        normalize_for_safety(w) in norm for w in SEVERE_SYMPTOM_WORDS
    )

    # Numeric glucose check. Digits are ASCII by now, whatever script was typed.
    for match in GLUCOSE_PATTERN.finditer(norm):
        try:
            value = int(match.group(1))
        except (TypeError, ValueError):
            continue

        # Convert to mg/dL once, then reason in one unit only.
        if glucose_unit == "mmol/L":
            mgdl = value * 18
            # A 1-2 digit number under an mmol/L profile is genuinely mmol/L.
            if value > 45:
                continue
        else:
            mgdl = float(value)

        # Symptoms turn a borderline reading into an urgent one. 65 alone is a
        # hypo to treat at home; 65 with confusion is not.
        if mgdl <= GLUCOSE_SEVERE_LOW_MGDL:
            return {"blocked": True, "response": EMERGENCY_RESPONSE, "tier": "emergency"}

        if mgdl < GLUCOSE_LOW_MGDL and has_symptom:
            return {"blocked": True, "response": EMERGENCY_RESPONSE, "tier": "emergency"}

        if mgdl >= GLUCOSE_HIGH_MGDL:
            return {"blocked": True, "response": EMERGENCY_RESPONSE, "tier": "emergency"}

    for word, word_nospace in _DOSING_NORM:
        if word and (word in norm or word_nospace in norm_nospace):
            return {"blocked": True, "response": DOSING_RESPONSE, "tier": "prescribing"}

    return {
                "blocked": True,
                "response": DOSING_RESPONSE_UR if language == "ur" else DOSING_RESPONSE_EN,
                "tier": "prescribing",
            }



# =====================================================
# Minimum-necessary context
# =====================================================
#
# Decides which profile CATEGORIES a given message actually needs, so the
# system prompt carries only what the question requires instead of the full
# medical record on every turn.
#
# Deliberately keyword-based rather than an LLM call: auditable (you can see
# exactly why a category fired), no added latency or cost, and it cannot
# silently misjudge relevance the way a model classification could.
#
# ALWAYS-ON, never filtered:
#   - allergies      (a keyword miss here is a safety gap, not a privacy win)
#   - known_facts    (exist precisely so the model stops re-asking)
#   - conditions     (kidney / heart / BP / pregnancy gate the mandatory food
#                     safety rules; omitting them is the dangerous direction)

MEDICATION_KEYWORDS = [
    "medicine", "medication", "drug", "pill", "tablet", "dose", "dosage",
    "insulin", "metformin", "glucophage", "side effect", "prescription",
    "dawa", "dawai", "goli",
    "دوا", "دوائی", "گولی", "انسولین", "خوراک",
]

HBA1C_KEYWORDS = [
    "hba1c", "a1c", "average glucose", "test result", "lab result",
    "ٹیسٹ", "رپورٹ", "نتیجہ",
]

COMPLICATION_KEYWORDS = [
    "complication", "nerve", "eye", "foot", "retinopathy", "neuropathy",
    "nephropathy", "vision", "wound", "ulcer", "numbness",
    "aankh", "paon", "zakhm", "sunn",
    "آنکھ", "پاؤں", "زخم", "سن", "نظر", "اعصاب",
]

GLUCOSE_KEYWORDS = [
    "sugar", "glucose", "reading", "level", "fasting", "spike", "high",
    "low", "trend", "cgm", "meter", "shuger",
    "shugar", "sheeta", "nashta se pehle",
    "شوگر", "گلوکوز", "ریڈنگ", "لیول", "نہار", "روزہ",
]

PHYSICAL_KEYWORDS = [
    "weight", "bmi", "diet", "exercise", "smoke", "smoking", "obese",
    "walk", "gym", "workout", "food", "eat", "meal",
    "wazan", "khana", "warzish", "chalna", "sigret",
    "وزن", "کھانا", "ورزش", "چہل", "سگریٹ", "کھا", "پی",
]

# Personal-advice phrasing under-triggers on keywords alone: "what should I
# eat" hits nothing specific yet clearly depends on the whole clinical picture.
PERSONALIZED_ADVICE_KEYWORDS = [
    "should i", "can i", "is it safe", "what about", "advice",
    "recommend", "help me", "what should", "for me",
    "kya main", "kya mujhe", "mere liye", "kar sakta", "kha sakta",
    "کیا میں", "کیا مجھے", "میرے لیے", "سکتا ہوں", "سکتی ہوں", "مشورہ",
]


def classify_relevant_categories(message: str) -> set[str]:
    """Which profile categories this message needs. Conditions are always in."""
    m = normalize_for_safety(message)

    # Never gated - see note above.
    categories: set[str] = {"conditions"}

    if any(normalize_for_safety(k) in m for k in MEDICATION_KEYWORDS):
        categories.add("medications")
    if any(normalize_for_safety(k) in m for k in HBA1C_KEYWORDS):
        categories.add("hba1c")
    if any(normalize_for_safety(k) in m for k in COMPLICATION_KEYWORDS):
        categories.add("complications")
    if any(normalize_for_safety(k) in m for k in GLUCOSE_KEYWORDS):
        categories.add("glucose")
    if any(normalize_for_safety(k) in m for k in PHYSICAL_KEYWORDS):
        categories.add("physical")

    if any(normalize_for_safety(k) in m for k in PERSONALIZED_ADVICE_KEYWORDS):
        categories.update({
            "medications", "hba1c", "complications", "glucose", "physical"
        })

    return categories


# =====================================================
# System Prompt Builder
# =====================================================

def build_system_prompt(profile: ProfileData, relevant: set[str]) -> str:

    prompt = """
You are GlycoAI, an AI diabetes education assistant designed for Pakistani patients.

Your role is to educate patients safely.

ABSOLUTE RULES

1. Never diagnose disease.

2. Never prescribe medicines.

3. Never recommend insulin doses.

4. Never recommend changing medication.

5. Never replace a doctor's advice.

6. If symptoms suggest an emergency,
advise immediate emergency medical care.

7. If medication questions require dose adjustment,
tell the patient to contact their doctor.

8. Always explain in a supportive,
friendly and easy-to-understand manner.
"""

    # -------------------------------------------------
    # Language
    # -------------------------------------------------

    if profile.language == "ur":
        prompt += "\nAlways respond in Urdu script.\n"

    elif profile.language == "en":
        prompt += "\nAlways respond only in English.\n"

    else:
        prompt += "\nRespond in the same language used by the patient.\n"

    # -------------------------------------------------
    # Role-based adaptation
    # -------------------------------------------------

    purpose = profile.purpose.strip().lower()

    if purpose == "patient":
        prompt += """

USER ROLE: PATIENT
This person has diabetes and manages their own care.
Use simple, warm, everyday language. Avoid jargon; explain any term you must use.
Give personalized advice grounded in their profile.
When citing sources, name patient-friendly organizations: American Diabetes
Association, WHO, IDF, NHS, Mayo Clinic.
Do not overwhelm them with research papers unless they ask.
"""

    elif purpose == "educational":
        prompt += """

USER ROLE: STUDENT / LEARNER
This person does NOT have diabetes and is learning about it.
Do NOT treat them as a patient. Never give personal management advice.
Explain concepts in a structured, educational way.
Name reputable learning sources: peer-reviewed journals (Diabetes Care,
The Lancet Diabetes and Endocrinology), review articles, clinical textbooks,
and trusted educational sites (CDC, WHO, IDF).
Suggest what to read more about.
State clearly this is general education, not personalized medical advice.
"""

    elif purpose == "professional":
        prompt += """

USER ROLE: HEALTHCARE PROFESSIONAL
This person is a clinician using the app as a reference.
Use appropriate clinical terminology; do not oversimplify.
Ground answers in evidence-based medicine.
Reference recognized guidelines by name: ADA Standards of Care, NICE, WHO,
IDF, AACE, EASD. Name the specific guideline where relevant.
Reference types of evidence (systematic reviews, meta-analyses, RCTs) and
journals (Diabetes Care, Lancet, BMJ, NEJM, JAMA) where appropriate.
This is a professional discussion, not patient education.
"""

    elif purpose == "caregiver":
        prompt += """

USER ROLE: CAREGIVER / FAMILY MEMBER
This person supports someone else who has diabetes.
Use simple, supportive language. Frame advice around helping their person.
Focus on practical caregiving: medication reminders, glucose monitoring,
meal planning, recognizing hypoglycemia and hyperglycemia, and when to seek
emergency care.
Name trusted patient-education sources (ADA, WHO, IDF, NHS), not research papers.
Emphasize supporting - not replacing - the patient's own doctor.

IMPORTANT: the profile below describes the PERSON THEY CARE FOR. Use it exactly
as you would for a patient. Do not fall back to generic advice on the grounds
that you are talking to a caregiver - you have this person's details, so use them.
"""

    # -------------------------------------------------
    # Source and link rules (ALL roles)
    # -------------------------------------------------

    prompt += """

SOURCE AND LINK RULES

Citing a source is OPTIONAL. Only mention a source when it genuinely adds value -
for a significant medical claim, a treatment guideline, or when the user asks
where to learn more. Do NOT attach a source or website to every answer. Simple,
everyday questions need no citation at all. Never repeat the same website across
messages out of habit.

When you DO cite:
Name the organization or guideline (for example "the ADA Standards of Care").
You may include ONLY the plain homepage of well-known organizations, exactly
as written here: diabetes.org, who.int, nice.org.uk, idf.org, nhs.uk,
mayoclinic.org, cdc.gov, pubmed.ncbi.nlm.nih.gov, cochranelibrary.com
NEVER invent or construct any other URL. Do NOT build deep links to specific
articles, PDFs, guideline pages, or search results. If you cannot cite a source
without a specific link, name it in words and stop there.
Never present a URL you are not certain of. A named source with no link is
always better than a fabricated link.
"""

    prompt += """

OUTPUT FORMATTING

The "message" field is shown as plain text inside a mobile chat bubble - it does
NOT render Markdown tables, headings, or pipe characters. Follow these rules:

- Never use a Markdown table. No "|" characters, no "|---|---|" separator rows.
- Never use "#" or "##" headings. To introduce a new point, just start a new
  short paragraph, optionally bolding a short label like **this**.
- For lists, write one item per line with a simple dash, in plain text.
- When listing multiple items (for example, several websites), write each as
  its own line - for example "International Diabetes Federation - idf.org" -
  never as a table.
"""

    # -------------------------------------------------
    # Patient Profile
    # -------------------------------------------------

    prompt += "\n========== PATIENT PROFILE ==========\n"

    prompt += f"Name: {profile.name}\n"

    prompt += f"Age: {profile.age}\n"

    prompt += f"Sex: {profile.sex}\n"

    prompt += f"Country: {profile.country}\n"

    prompt += f"Diabetes Type: {profile.diabetes_type}\n"

    prompt += f"Diagnosis Year: {profile.diagnosis_year or 'Unknown'}\n"

    prompt += f"Glucose Unit: {profile.glucose_unit}\n"

    # Allergies: ALWAYS included. A keyword miss here would be a safety gap,
    # not a privacy win - the food rules below depend on it being present.
    if profile.allergies:
        prompt += f"\nALLERGIES: {', '.join(profile.allergies)}\n"
        prompt += (
            "CRITICAL: never recommend any food, ingredient or medicine that "
            "matches or contains one of these. If a question touches an "
            "ingredient close to an allergy, name the conflict explicitly "
            "rather than quietly avoiding it.\n\n"
        )

    if "medications" in relevant:
        prompt += f"Insulin Type: {profile.insulin_type or 'Unknown'}\n"
        prompt += f"Current Medicines: {', '.join(profile.medications) if profile.medications else 'None'}\n"

    if "glucose" in relevant:
        prompt += f"Glucose Monitoring: {profile.glucose_monitoring or 'Unknown'}\n"
        prompt += f"Previous Severe Hypoglycemia: {profile.severe_hypoglycemia or 'Unknown'}\n"

    # Conditions and complications come from one onboarding screen, so treat
    # them as a single combined list everywhere.
    all_conditions = [c for c in (profile.other_conditions + profile.complications) if c]

    prompt += f"Other Medical Conditions: {', '.join(all_conditions) if all_conditions else 'None'}\n"

    if "hba1c" in relevant:
        if profile.hba1c is not None:
            line = f"HbA1c: {profile.hba1c}%"
            line += (f" (measured {profile.hba1c_date})" if profile.hba1c_date
                     else " (measurement date unknown - may be outdated)")
            prompt += line + "\n"
        else:
            prompt += "HbA1c: Not recorded\n"

    # BMI calculation
    if "physical" in relevant and profile.weight_kg and profile.height_cm and profile.height_cm > 0:
        height_m = profile.height_cm / 100
        bmi = profile.weight_kg / (height_m * height_m)
        bmi_rounded = round(bmi, 1)

        prompt += f"BMI: {bmi_rounded}"

        if bmi < 18.5:
            prompt += " (underweight)\n"
        elif bmi < 25:
            prompt += " (normal weight)\n"
        elif bmi < 30:
            prompt += " (overweight)\n"
        else:
            prompt += " (obese)\n"

        prompt += ("Factor BMI into diet and exercise advice, but raise weight "
                   "gently and without shaming. Focus on health, not appearance.\n")

    # Smoking
    if "physical" in relevant and profile.smoking_status == "Current":
        prompt += ("PATIENT SMOKES: Smoking sharply raises cardiovascular and "
                   "kidney risk in diabetes. Where relevant, gently encourage "
                   "quitting and mention local support exists - but do not lecture "
                   "or repeat it in every answer.\n")
    elif "physical" in relevant and profile.smoking_status == "Former":
        prompt += "Patient is a former smoker - acknowledge this positively if it comes up.\n"

    prompt += "=====================================\n"

    # -------------------------------------------------
    # Glucose history
    # -------------------------------------------------

    if "glucose" in relevant and profile.glucose_summary:
        prompt += f"""
========== GLUCOSE HISTORY ==========
{profile.glucose_summary}

Use this data actively:
- If they ask how they are doing, answer from these numbers, not generically.
- Never ask for their recent readings when the data is above - you can see it.
- If the most recent reading is more than 24 hours old, you may ask for a
  current one, acknowledging what you already have.
- Comment on trends only when relevant; do not recite statistics every message.
- If time-in-range is below 50% or there are repeated lows, gently raise it
  and encourage doctor review.
=====================================
"""
    else:
        prompt += """
========== GLUCOSE HISTORY ==========
No glucose readings have been recorded in the app yet.
You may ask for a recent reading when it would change your advice.
=====================================
"""

    # -------------------------------------------------
    # Remembered Facts From Past Conversations
    # -------------------------------------------------

    if profile.known_facts:
        prompt += "\n========== REMEMBERED FROM PAST CONVERSATIONS ==========\n"
        for fact in profile.known_facts:
            prompt += f"- {fact}\n"
        prompt += (
            "\nThese are things the patient told you in earlier sessions.\n"
            "Treat them as patient-reported, not verified medical records.\n"
            "Use them to avoid asking questions already answered.\n"
            "If a remembered fact conflicts with the structured profile above, "
            "trust the profile and ask the patient to clarify.\n"
            "If a fact is old and may have changed, confirm it before relying on it.\n"
        )
        prompt += "=======================================================\n"

    # -------------------------------------------------
    # RESPONSE FORMAT  (structured output)
    # -------------------------------------------------

    prompt += """

========== RESPONSE FORMAT ==========

Return ONLY a JSON object. No preamble, no explanation, no markdown code fences.

{
  "tier": "emergency" | "prescribing" | "education" | "personal",
  "needs_context": true or false,
  "message": "what to say to the user",
  "quick_replies": ["short option", "short option"]
}

FIELD RULES

"tier" - classify the user's question:
  emergency    - symptoms or readings suggesting urgent danger
  prescribing  - asking what dose to take, or whether to start/stop/change a medicine
  education    - general knowledge, not about this person's own management
  personal     - advice about this person's own diet, exercise, or daily management

"needs_context" - true ONLY when you are asking a question instead of advising.

"message" - the text shown in the chat bubble.

"quick_replies" - tappable buttons sent as the user's next message.
  Maximum 4. Each under 30 characters.
  When needs_context is true: likely ANSWERS to the question you just asked,
  and ALWAYS include one meaning "I don't know".
  When needs_context is false: 2 or 3 natural follow-up questions they may
  want to ask next, or an empty list if none fit.
  Write them in the same language as "message".

========== HOW TO DECIDE ==========

TIER: EMERGENCY
Answer immediately and fully. needs_context MUST be false.
NEVER ask a clarifying question first. Tell them what to do right now and to
seek urgent care. Safety before information gathering, always.

YOU ARE THE PRIMARY EMERGENCY DETECTOR.
A keyword filter runs before you, but it only catches phrasings someone
anticipated. It WILL miss things - especially in Urdu, where the same word has
several spellings, and in Roman Urdu, where spelling is not standardised.
Do not assume that a message reaching you has already been cleared as safe.

Read every message for danger in whatever language and script it arrives in.
Treat as EMERGENCY, regardless of wording:
  - loss or clouding of consciousness, not waking, unresponsiveness
    (بیہوش، ہوش نہیں، جاگ نہیں رہا، behosh, hosh nahi)
  - difficulty breathing (سانس نہیں آ رہی، saans nahi)
  - seizure or convulsions (دورہ، مرگی، جھٹکے، daura, mirgi)
  - chest pain (سینے میں درد، seene me dard)
  - one-sided weakness, slurred speech, sudden confusion (فالج، falij)
  - repeated vomiting alongside high sugar - possible ketoacidosis
  - glucose at or below 54 mg/dL (3 mmol/L), or at or above 400 mg/dL
    (22 mmol/L), whether written in Western digits or Urdu digits (۰-۹)
  - a caregiver describing any of the above happening to someone else

Err toward treating it as an emergency. Telling someone to seek urgent care
when they did not strictly need to costs them a wasted trip. Failing to tell
them when they did need to can cost far more.

TIER: PRESCRIBING
needs_context MUST be false. Never give a dose, never suggest starting,
stopping, or changing a medicine - no matter how much detail they provide.
More context NEVER unlocks dose advice. Explain warmly why you cannot, and
direct them to their doctor.

TIER: EDUCATION
Answer directly. needs_context is false. Nothing personal is required to
explain what HbA1c is or how insulin works.

TIER: PERSONAL - APPLY THE GATE
Work through this silently before writing anything:

STEP 1 - What would genuinely change my recommendation here?
STEP 2 - Which of those do I already know, from the profile, glucose history,
         remembered facts, or earlier in this conversation?
STEP 3 - Is anything still missing that would MATERIALLY change my advice?

If nothing material is missing:
  needs_context = false. Answer fully, using the profile explicitly by name.

If something material IS missing:
  needs_context = true.
  Ask exactly ONE question - the single most important one.
  Acknowledge what you already know, so they feel heard.
  DO NOT give the recommendation in the same message. No portion sizes, no
  numbers, no "but generally speaking...". Withholding is the entire point -
  a recommendation plus a question is the failure this rule exists to prevent.
  A short neutral framing sentence is fine. A recommendation is not.

WHEN THE USER CANNOT ANSWER
If they say "I don't know" or decline, you may ask ONE easier question they can
realistically answer - how they have been feeling lately, whether they check at
all, what they normally eat it with. Ask only if it would genuinely narrow things
down. If they cannot answer that either, STOP asking. Never loop, never stall,
never leave them with nothing.

ANSWERING WITH INCOMPLETE INFORMATION
When you answer without the context you wanted, your answer MUST be visibly less
precise than a fully informed one. Precision is a signal of certainty - do not
fake it.

NEVER give a specific number when you do not know their current control.
No gram weights, no piece counts, no cup measures, no "X to Y pieces".
A precise number reads as personalised medical advice and will be followed
exactly, which is dangerous when it was a guess.

Instead:
- Give qualitative guidance - "a small portion", "less than you would normally eat"
- Name plainly what you could not account for, and why it matters
- Give ONE concrete action that would let you answer properly next time.
  This app has a Tracker tab - tell them to add a reading there and ask again.
- If being wrong could genuinely harm them, say so and route to their doctor
  or diabetes educator

INSULIN AND FOOD PORTIONS
For anyone on insulin, food amounts are governed by carbohydrate counting and
their own insulin plan - NOT by a fixed portion that applies to everyone.
Never imply a universal safe portion exists for them. Explain the carb-counting
principle, and send them to their doctor or diabetes educator for actual numbers.
Giving a type 1 patient a piece count is a mistake, even when it sounds cautious.

WHAT YOU MUST NEVER ASK
Anything already in the profile above - their diabetes type, medicines,
conditions, age, sex, HbA1c if recorded, or recent readings if glucose history
is present. Asking again looks careless and breaks trust.

========== FOOD AND DIET QUESTIONS ==========

Food questions are TIER: PERSONAL. The gate above applies to them in full -
this section describes what to do once the gate has passed.

When you DO answer a food question, structure it as:
1. Whether it can be eaten, with a specific portion
2. The reason, tied to this person's own profile
3. Which of their conditions or medicines affect this
4. What to watch for afterwards

If they have kidney disease, heart disease, or high blood pressure, you MUST
say how that condition specifically affects this food before any portion advice.

If HbA1c is above 9, state that this advice assumes control is improving,
and encourage doctor review.

Never present a food as simply safe without naming the condition or medicine
that makes it conditional.

========== WORKED EXAMPLES ==========

User: "What is diabetes?"
{"tier":"education","needs_context":false,"message":"...explanation...",
 "quick_replies":["What causes type 2?","How is it diagnosed?"]}

User: "How many units should I take?"
{"tier":"prescribing","needs_context":false,"message":"I can't work out insulin
 doses - that has to come from your doctor...","quick_replies":[]}

User: "Can I eat mango?" - profile has type 2, Glucophage, no recent readings
{"tier":"personal","needs_context":true,"message":"Mango is a big part of summer
 here, so let's work out what's right for you. I can see you're on Glucophage.
 Before I suggest a portion - what was your last blood sugar reading?",
 "quick_replies":["Under 140","140 to 200","Over 200","I don't know"]}

User: "Can I eat mango?" - profile has type 2, kidney disease, good recent data
{"tier":"personal","needs_context":true,"message":"With kidney disease, mango
 matters for two reasons - sugar and potassium - and the safe amount depends on
 your kidney stage. Do you know what stage yours is?",
 "quick_replies":["Stage 1 or 2","Stage 3","Stage 4 or 5","I don't know"]}

BAD - never do this:
{"needs_context":true,"message":"You can have about half a cup. By the way,
 what was your last reading?"}
That gives the recommendation anyway, which defeats the gate entirely.

User: "I don't know" - after being asked for their reading, profile is type 1
BAD: "Yes, a small portion - about 8 to 10 small pieces." A precise piece count
 given without knowing their control, to someone whose portions are set by carb
 counting and insulin. Precise numbers imply certainty you do not have.
GOOD:
{"tier":"personal","needs_context":false,"message":"No problem, that happens.
 Here's what I can tell you without it.\n\nMango is high in natural sugar, so
 the principle is a small portion rather than a full one - and eaten with a
 meal or some protein rather than on its own.\n\nWhat I can't tell you is the
 right amount for YOU. With type 1, how much fruit you can have isn't a fixed
 number - it depends on carbohydrate counting and your insulin plan, which your
 doctor or diabetes educator sets with you. Anyone giving you a piece count
 without knowing that is guessing.\n\nIf you add a reading in the Tracker tab
 after you next check, ask me again and I can be much more specific.",
 "quick_replies":["How does carb counting work?","What should I watch for?"]}
"""

    # -------------------------------------------------
    # Disease-specific Guidance
    # -------------------------------------------------

    diabetes_type = profile.diabetes_type.strip().lower()

    if diabetes_type == "type1":
        prompt += """

TYPE 1 DIABETES

Focus on insulin, hypoglycemia prevention, carbohydrate counting, sick-day rules.
"""

    elif diabetes_type == "type2":
        prompt += """

TYPE 2 DIABETES

Focus on diet, exercise, weight management, oral medicines.
"""

    elif diabetes_type == "gestational":
        prompt += """

GESTATIONAL DIABETES

Always recommend close obstetric follow-up.
Avoid medication recommendations.
"""

    elif diabetes_type == "none":
        prompt += """

EDUCATION MODE - NO DIABETES

This user does not have diabetes. They are learning, or trying to understand
the condition for a family member.

Answer as a diabetes educator giving general information.
Do NOT address them as a patient.
Do NOT assume they have symptoms, medications, or complications.
Do NOT give them personal management advice.
Almost every question here is TIER: EDUCATION, so the gate rarely applies.
If they ask about a specific person's care, remind them that person should
consult their own doctor.
"""

    # -------------------------------------------------
    # HbA1c
    # -------------------------------------------------

    if profile.hba1c is not None:

        if profile.hba1c >= 9:
            prompt += f"""

IMPORTANT

HbA1c is {profile.hba1c}%, which is high. Encourage early doctor review, and
state that food and activity advice assumes control is being worked on.
"""

        elif profile.hba1c >= 7:
            prompt += f"""

HbA1c is {profile.hba1c}%. Encourage continued diabetes management.
"""

    else:
        prompt += """

HbA1c is not recorded. If a question genuinely depends on overall control and
there is no recent glucose data either, asking for their last HbA1c is
reasonable - but only if it would actually change your advice.
"""

    # -------------------------------------------------
    # Other Conditions  (now reads the combined list)
    # -------------------------------------------------

    conditions_text = " ".join(all_conditions).lower()

    if "kidney" in conditions_text:
        prompt += """

KIDNEY DISEASE ON FILE
Avoid fixed protein recommendations.
Avoid potassium advice without knowing their kidney stage.
For any potassium-rich food (bananas, mangoes, oranges, potatoes, tomatoes,
dates, coconut water), you MUST address potassium before giving a portion.
Recommend nephrologist or renal dietitian when appropriate.
"""

    if "heart" in conditions_text:
        prompt += """

HEART DISEASE ON FILE
Recommend heart-healthy diet. Address sodium and saturated fat for food questions.
Avoid strenuous exercise advice; suggest clearing activity changes with their doctor.
"""

    if "blood pressure" in conditions_text:
        prompt += """

HIGH BLOOD PRESSURE ON FILE
Recommend lower sodium diet. Encourage regular BP monitoring.
Mention sodium content when discussing any packaged or salty food.
"""

    if "pregnan" in conditions_text:
        prompt += """

PREGNANCY ON FILE
Never recommend medication changes.
Encourage obstetric follow-up.
Be more cautious than usual; prefer asking over assuming.
"""

    if "foot" in conditions_text or "ulcer" in conditions_text:
        prompt += """

FOOT ULCER OR AMPUTATION HISTORY ON FILE
Emphasize daily foot checks and proper footwear.
Any new foot wound, colour change, or numbness warrants same-week medical review.
"""

    # -------------------------------------------------
    # Response Style
    # -------------------------------------------------

    if profile.response_style == "simple":
        prompt += """

Use short sentences and simple English. Maximum four short paragraphs.
"""
    else:
        prompt += """

Provide detailed explanations. Explain step by step.
"""

    prompt += "\n========== END PROFILE ==========\n"

    return prompt


# =====================================================
# Structured response parsing
# =====================================================

def parse_model_reply(raw: str) -> dict:
    """Parse the model's JSON reply, falling back to plain text if malformed."""

    text = (raw or "").strip()

    fallback = {
        "response": text or "Sorry, I could not process that right now. Please try again.",
        "needs_context": False,
        "quick_replies": [],
        "tier": None,
    }

    if not text:
        return fallback

    cleaned = text
    if cleaned.startswith("```"):
        cleaned = re.sub(r"^```[a-zA-Z]*\s*", "", cleaned)
        cleaned = re.sub(r"\s*```$", "", cleaned)
        cleaned = cleaned.strip()

    # If there is prose around the object, take the outermost braces.
    if not cleaned.startswith("{"):
        start = cleaned.find("{")
        end = cleaned.rfind("}")
        if start == -1 or end == -1 or end <= start:
            return fallback
        cleaned = cleaned[start:end + 1]

    try:
        data = json.loads(cleaned)
    except json.JSONDecodeError:
        return fallback

    if not isinstance(data, dict):
        return fallback

    message = str(data.get("message") or "").strip()
    if not message:
        return fallback

    replies_raw = data.get("quick_replies") or []
    replies = []
    if isinstance(replies_raw, list):
        for item in replies_raw:
            value = str(item).strip()
            if value:
                replies.append(value[:60])
    replies = replies[:4]

    tier = str(data.get("tier") or "").strip().lower() or None
    needs_context = bool(data.get("needs_context"))

    # Enforce the tier invariants in code, not just in the prompt.
    if tier in ("emergency", "prescribing"):
        needs_context = False

    return {
        "response": message,
        "needs_context": needs_context,
        "quick_replies": replies,
        "tier": tier,
    }


# =====================================================
# Fact Extraction Endpoint
# =====================================================

class ExtractRequest(BaseModel):
    conversation: list[dict] = Field(default_factory=list)
    existing_facts: list[str] = Field(default_factory=list)


@app.post("/api/v1/extract-facts")
async def extract_facts(request: ExtractRequest, uid: str = Depends(current_uid)):

    enforce_rate_limit(uid)

    if len(request.conversation) < 2:
        return {"facts": request.existing_facts}

    transcript = "\n".join(
        f"{m.get('role', '')}: {m.get('content', '')}"
        for m in request.conversation
    )

    existing = "\n".join(f"- {f}" for f in request.existing_facts) or "None yet"

    extraction_prompt = f"""Extract durable clinical facts from this
diabetes education conversation.

EXISTING KNOWN FACTS:
{existing}

CONVERSATION:
{transcript}

Return a JSON array of short factual strings.

INCLUDE only things that remain true across sessions:
- symptoms the patient reports experiencing regularly
- foods they say they eat often or avoid
- exercise habits and routines
- what their doctor has told them
- devices they use, such as a glucometer or CGM
- practical constraints, for example cost or work schedule
- family or caregiver situation relevant to their care
- answers they gave to clarifying questions, for example their kidney
  stage, insulin type, or usual pre-meal readings

EXCLUDE:
- anything already in the existing facts list
- one-off questions with no lasting information
- general education the assistant provided
- greetings, thanks, and small talk
- anything speculative or uncertain

Merge with existing facts. If a new statement updates an old fact,
replace the old one. Keep the total under 15 facts, dropping the
least clinically useful if needed.

Each fact must be one short sentence starting with "Patient".

Return ONLY the JSON array, no other text.

Example: ["Patient walks 30 minutes daily", "Patient uses a glucometer twice a week"]
"""

    try:

        response = client.messages.create(
            model="claude-haiku-4-5",
            max_tokens=800,
            temperature=0,
            messages=[{"role": "user", "content": extraction_prompt}]
        )

        raw = response.content[0].text.strip()

        raw = raw.removeprefix("```json").removeprefix("```").removesuffix("```").strip()

        facts = json.loads(raw)

        if not isinstance(facts, list):
            return {"facts": request.existing_facts}

        clean = [str(f).strip() for f in facts if str(f).strip()][:15]

        return {"facts": clean}

    except Exception as e:
        print("FACT EXTRACTION ERROR:", e)
        return {"facts": request.existing_facts}


# =====================================================
# Personalized Tips Endpoint
# =====================================================

class TipsRequest(BaseModel):
    profile: ProfileData


@app.post("/api/v1/tips")
async def get_tips(request: TipsRequest, uid: str = Depends(current_uid)):

    enforce_rate_limit(uid)

    p = request.profile

    all_conditions = [c for c in (p.other_conditions + p.complications) if c]

    context = f"Age {p.age}, {p.sex}, diabetes type: {p.diabetes_type}."
    if p.medications:
        context += f" Medicines: {', '.join(p.medications)}."
    if all_conditions:
        context += f" Other conditions: {', '.join(all_conditions)}."
    if p.hba1c is not None:
        context += f" HbA1c: {p.hba1c}%."
    if p.glucose_summary:
        context += f" Recent glucose: {p.glucose_summary}"
    if p.smoking_status == "Current":
        context += " Patient currently smokes."

    language_note = "Write in Urdu script." if p.language == "ur" else "Write in simple English."

    role_note = {
        "patient": "These are for a person managing their own diabetes.",
        "educational": "These are general educational facts for a student, not personal advice.",
        "professional": "These are clinical practice points for a healthcare professional.",
        "caregiver": "These are practical points for someone caring for a person with diabetes.",
    }.get(p.purpose, "These are for a person managing their own diabetes.")

    tips_prompt = f"""Write 6 short, practical diabetes tips.

PERSON: {context}
CONTEXT: {role_note}
LANGUAGE: {language_note}

RULES:
- Each tip one or two sentences, plain and specific.
- Tailor to this person's actual profile above - mention their conditions
  or medicines where relevant.
- Never recommend medication or insulin doses.
- Never diagnose.
- Practical and encouraging, not frightening.
- Suitable for Pakistan: mention local foods and habits where natural.

Return ONLY a JSON array of 6 strings, no other text.
Example: ["Walk for 20 minutes after dinner.", "Check your feet daily for cuts."]
"""

    try:
        response = client.messages.create(
            model="claude-haiku-4-5",
            max_tokens=900,
            temperature=0.7,
            messages=[{"role": "user", "content": tips_prompt}]
        )

        raw = response.content[0].text.strip()
        raw = raw.removeprefix("```json").removeprefix("```").removesuffix("```").strip()

        tips = json.loads(raw)

        if not isinstance(tips, list):
            raise ValueError("not a list")

        clean = [str(t).strip() for t in tips if str(t).strip()][:8]
        return {"tips": clean}

    except Exception as e:
        print("TIPS ERROR:", e)
        return {"tips": [
            "Check your blood sugar regularly and write down the readings.",
            "Take your medicines at the same time each day.",
            "Walk for 20 to 30 minutes most days if your doctor agrees.",
            "Drink water instead of sugary drinks and juices.",
            "Check your feet every day for cuts or sores.",
            "Keep your regular appointments with your doctor."
        ]}


# =====================================================
# Health Check
# =====================================================

@app.get("/health")
def health():
    return {
        "status": "running",
        "app": "GlycoAI",
        "version": "1.3",
        "language": "English / urdu",
        "auth_required": REQUIRE_AUTH,
        "auth_ready": _firebase_ready,
    }


# =====================================================
# Chat Endpoint
# =====================================================

# Raised from 6: the gate asks follow-up questions, so the model needs to see
# enough turns to know what it already asked and what was answered.
MAX_HISTORY = 14


@app.post("/api/v1/chat")
async def chat(request: ChatRequest, uid: str = Depends(current_uid)):

    enforce_rate_limit(uid)
    check_attachment_size(request)

    # ---------------------------------------------
    # Safety Check
    # ---------------------------------------------

    safety = check_safety(
        request.message,
        request.profile.glucose_unit,
        request.profile.language,
    )

    if safety["blocked"]:
        return {
            "response": safety["response"],
            "safety_triggered": True,
            "needs_context": False,
            "quick_replies": [],
            # Pass the real tier through. Hardcoding "emergency" here made a
            # dosing refusal render as a red emergency card - a false alarm on
            # a routine question, which is exactly what erodes trust in the
            # ones that matter.
            "tier": safety.get("tier", "emergency"),
        }

    # ---------------------------------------------
    # Build Claude System Prompt
    # ---------------------------------------------

    relevant = classify_relevant_categories(request.message)
    system_prompt = build_system_prompt(request.profile, relevant)

    # Log the relevance decision only - never the profile itself. Writing
    # allergies, HbA1c and medications into Railway's logs on every message
    # would be storing PHI in a place nobody is auditing.
    print(f"=== CONTEXT: {sorted(relevant)} ===")

    messages = list(request.conversation_history[-MAX_HISTORY:])

    # ---------------------------------------------
    # Image Message
    # ---------------------------------------------

    if request.image_data:

        messages.append({
            "role": "user",
            "content": [
                {
                    "type": "image",
                    "source": {
                        "type": "base64",
                        "media_type": request.image_type or "image/jpeg",
                        "data": request.image_data
                    }
                },
                {
                    "type": "text",
                    "text": request.message.strip()
                    if request.message.strip()
                    else "Please analyze this diabetes-related image."
                }
            ]
        })

    # ---------------------------------------------
    # PDF / Document Message
    # ---------------------------------------------

    elif request.document_data:

        messages.append({
            "role": "user",
            "content": [
                {
                    "type": "document",
                    "source": {
                        "type": "base64",
                        "media_type": request.document_type
                        or "application/pdf",
                        "data": request.document_data
                    },
                    "title": request.document_name
                    or "Medical Document"
                },
                {
                    "type": "text",
                    "text": request.message.strip()
                    if request.message.strip()
                    else "Please analyze this medical report."
                }
            ]
        })

    # ---------------------------------------------
    # Normal Text Chat
    # ---------------------------------------------

    else:

        messages.append({
            "role": "user",
            "content": request.message
        })

    # ---------------------------------------------
    # Claude API Call
    # ---------------------------------------------

    try:

        response = client.messages.create(

            model="claude-sonnet-4-6",

            max_tokens=1200,

            temperature=0.3,

            system=system_prompt,

            messages=messages,

            # Nudges the model straight into the JSON object.
            stop_sequences=[]
        )

        parsed = parse_model_reply(response.content[0].text)

        return {
            "response": parsed["response"],
            "safety_triggered": False,
            "needs_context": parsed["needs_context"],
            "quick_replies": parsed["quick_replies"],
            "tier": parsed["tier"],
        }

    except Exception as e:
        print("CHAT ERROR:", e)

        return {
            "response": "Sorry, I could not process that right now. Please try again in a moment.",
            "safety_triggered": False,
            "needs_context": False,
            "quick_replies": [],
            "tier": None,
        }