import sys
sys.stdout.reconfigure(encoding="utf-8")

import os
import re
import json
from fastapi import FastAPI
from pydantic import BaseModel, Field
from anthropic import Anthropic
from dotenv import load_dotenv

load_dotenv()

app = FastAPI(
    title="GlycoAI API",
    version="1.0"
)

client = Anthropic(
    api_key=os.getenv("ANTHROPIC_API_KEY")
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
# Centralized Glucose Safety Thresholds
# =====================================================
# Single source of truth. The Android Tracker (Glucosesuggestion.kt)
# already uses the same 70 / 400 mg/dL boundary values as isUrgentReading -
# this is where a clinician should review/update them going forward.
#
# mg/dL is the canonical unit; mmol/L values are converted for comparison.
GLUCOSE_SEVERE_LOW_MGDL = 54
GLUCOSE_LOW_MGDL = 70
GLUCOSE_HIGH_MGDL = 400


def _to_mgdl(value: float, unit: str) -> float:
    return value * 18 if "mmol" in unit.lower() else value


# Matches "sugar is 45", "glucose 38", "sugar: 320 mg/dl", "shuger 3.1 mmol/l", etc.
_GLUCOSE_PATTERN = re.compile(
    r"(?:sugar|glucose|shuger|bg)\D{0,10}?(\d{1,3}(?:\.\d+)?)\s*(mmol\s*/?\s*l|mg\s*/?\s*dl)?",
    re.IGNORECASE
)


def extract_glucose_reading(message: str, profile_unit: str = "mg/dL"):
    """Returns (value_mgdl, raw_value, raw_unit) or None if no reading found."""
    match = _GLUCOSE_PATTERN.search(message)
    if not match:
        return None
    raw_value = float(match.group(1))
    raw_unit = match.group(2) or profile_unit
    return (_to_mgdl(raw_value, raw_unit), raw_value, raw_unit)


# =====================================================
# Emergency Detection
# =====================================================

EMERGENCY_WORDS = [
    "unconscious", "passed out", "not breathing", "can't breathe",
    "difficulty breathing", "seizure", "stroke", "heart attack",
    "chest pain", "collapse", "behosh",
    "sugar 30", "sugar 40", "sugar 50",
    "glucose 30", "glucose 40", "glucose 50"
]

DOSING_WORDS = [
    "how many units", "how much insulin", "increase insulin",
    "reduce insulin", "insulin dose", "change insulin",
    "dose bata", "dose batao", "kitni insulin", "insulin kitni"
]

SEVERE_SYMPTOM_WORDS = [
    "confus", "can't think straight", "disoriented", "slurring",
    "shaking", "sweating heavily", "vomit", "can't wake", "unconscious",
    "passed out", "not breathing", "can't breathe", "difficulty breathing",
    "seizure", "chest pain", "collapse", "behosh",
    "dizzy", "dizziness", "lightheaded", "light-headed", "faint", "fainting"
]


def check_safety(message: str, profile_unit: str = "mg/dL"):

    message_lower = message.lower()

    # --- Keyword-based emergency phrases ---
    for word in EMERGENCY_WORDS:
        if word in message_lower:
            return {
                "blocked": True,
                "response":
                    "🚨 This may be a medical emergency. "
                    "Please call your local emergency services "
                    "(1122 in Pakistan) or go to the nearest hospital immediately."
            }

    # --- Numeric glucose value + symptom context ---
    reading = extract_glucose_reading(message, profile_unit)
    if reading:
        mgdl, raw_value, raw_unit = reading
        has_symptom = any(w in message_lower for w in SEVERE_SYMPTOM_WORDS)

        if mgdl <= GLUCOSE_SEVERE_LOW_MGDL or (mgdl < GLUCOSE_LOW_MGDL and has_symptom):
            return {
                "blocked": True,
                "response":
                    "🚨 A reading this low, especially with what you're describing, "
                    "may require urgent attention. If you're able to, take fast-acting "
                    "sugar now, and please call 1122 or go to the nearest hospital "
                    "if you don't improve quickly or feel worse."
            }

        if mgdl > GLUCOSE_HIGH_MGDL and has_symptom:
            return {
                "blocked": True,
                "response":
                    "🚨 A reading this high along with those symptoms may require "
                    "urgent attention. Please call 1122 or go to the nearest hospital."
            }
        # High value with NO symptoms, or a low-but-not-severe value with no
        # symptoms: deliberately NOT blocked here - falls through to the normal
        # AI response, since the number alone shouldn't force an emergency reply.

    # --- Dosing questions ---
    for word in DOSING_WORDS:
        if word in message_lower:
            return {
                "blocked": True,
                "response":
                    "I cannot recommend or calculate insulin or medication doses. "
                    "Please follow your doctor's instructions or contact your healthcare provider."
            }

    return {
        "blocked": False,
        "response": None
    }


# =====================================================
# Minimum-Necessary Context Classifier
# =====================================================
#
# Decides which profile CATEGORIES are relevant to this specific message,
# so build_system_prompt only sends Claude what the question actually needs
# instead of the full medical profile on every single turn.
#
# Deliberately keyword-based rather than an LLM call: it's auditable (you can
# see exactly why a category fired), adds no latency/cost, and can't silently
# misjudge relevance the way a model classification could.
#
# Two categories are NEVER filtered by this function - callers should treat
# allergies and known_facts as always-on. See build_system_prompt.

MEDICATION_KEYWORDS = [
    "medicine", "medication", "drug", "pill", "tablet", "dose", "dosage",
    "insulin", "metformin", "glucophage", "side effect", "prescription",
    "dawa", "dawai"
]

CONDITION_KEYWORDS = [
    "condition", "kidney", "heart", "blood pressure", "pregnan",
    "disease", "gurda", "dil"
]

HBA1C_KEYWORDS = [
    "hba1c", "a1c", "average glucose", "test result", "lab result"
]

COMPLICATION_KEYWORDS = [
    "complication", "nerve", "eye", "foot", "retinopathy", "neuropathy",
    "nephropathy", "vision", "wound", "ulcer", "numbness"
]

GLUCOSE_KEYWORDS = [
    "sugar", "glucose", "reading", "level", "fasting", "spike", "high",
    "low", "trend", "cgm", "meter", "shuger"
]

PHYSICAL_KEYWORDS = [
    "weight", "bmi", "diet", "exercise", "smoke", "smoking", "obese",
    "walk", "gym", "workout"
]

# If the question reads as a personal-advice request, keyword matching alone
# tends to under-include - "what should I eat" doesn't hit any single
# category keyword but clearly depends on the whole clinical picture.
PERSONALIZED_ADVICE_KEYWORDS = [
    "should i", "can i", "is it safe", "what about", "advice",
    "recommend", "help me", "what should"
]


def classify_relevant_categories(message: str) -> set[str]:
    m = message.lower()
    categories: set[str] = set()

    if any(k in m for k in MEDICATION_KEYWORDS):
        categories.add("medications")
    if any(k in m for k in CONDITION_KEYWORDS):
        categories.add("conditions")
    if any(k in m for k in HBA1C_KEYWORDS):
        categories.add("hba1c")
    if any(k in m for k in COMPLICATION_KEYWORDS):
        categories.add("complications")
    if any(k in m for k in GLUCOSE_KEYWORDS):
        categories.add("glucose")
    if any(k in m for k in PHYSICAL_KEYWORDS):
        categories.add("physical")

    if any(k in m for k in PERSONALIZED_ADVICE_KEYWORDS):
        categories.update({
            "medications", "conditions", "hba1c",
            "complications", "glucose", "physical"
        })

    return categories


# =====================================================
# System Prompt Builder
# =====================================================

def build_system_prompt(profile: ProfileData, relevant_categories: set[str]) -> str:

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

This reply is shown as plain text inside a mobile chat bubble - it does NOT
render Markdown tables, headings, or pipe characters. Follow these rules:

- Never use a Markdown table. No "|" characters, no "|---|---|" separator rows.
- Never use "#" or "##" headings. To introduce a new point, just start a new
  short paragraph, optionally bolding a short label like **this**.
- For lists, write one item per line with a simple dash, in plain text.
- When listing multiple items (for example, several websites), write each as
  its own line - for example "International Diabetes Federation - idf.org" -
  never as a table.
"""

    # -------------------------------------------------
    # Patient Profile (minimum-necessary: only relevant
    # categories are included below, plus the always-on set)
    # -------------------------------------------------

    prompt += "\n========== PATIENT PROFILE ==========\n"

    prompt += f"Name: {profile.name}\n"
    prompt += f"Age: {profile.age}\n"
    prompt += f"Sex: {profile.sex}\n"
    prompt += f"Country: {profile.country}\n"
    prompt += f"Diabetes Type: {profile.diabetes_type}\n"
    prompt += f"Diagnosis Year: {profile.diagnosis_year or 'Unknown'}\n"

    # Allergies: ALWAYS included, never gated by relevance. A keyword miss
    # here would be a safety gap, not a privacy win - the existing food/
    # medicine safety rule below depends on this always being present.
    if profile.allergies:
        prompt += f"\n⚠️ ALLERGIES: {', '.join(profile.allergies)}\n"
        prompt += (
            "CRITICAL: Never recommend any food, ingredient, or medicine that "
            "matches or contains one of these allergens. If a food question "
            "touches on an ingredient close to an allergy, name the conflict "
            "explicitly rather than silently avoiding it.\n\n"
        )

    prompt += f"Glucose Unit: {profile.glucose_unit}\n"

    if "medications" in relevant_categories:
        prompt += f"Insulin Type: {profile.insulin_type or 'Unknown'}\n"
        prompt += f"Current Medicines: {', '.join(profile.medications) if profile.medications else 'None'}\n"

    if "glucose" in relevant_categories:
        prompt += f"Glucose Monitoring: {profile.glucose_monitoring or 'Unknown'}\n"
        prompt += f"Previous Severe Hypoglycemia: {profile.severe_hypoglycemia or 'Unknown'}\n"

    if "conditions" in relevant_categories:
        prompt += f"Other Medical Conditions: {', '.join(profile.other_conditions) if profile.other_conditions else 'None'}\n"

    if "hba1c" in relevant_categories and profile.hba1c is not None:
        hba1c_line = f"HbA1c: {profile.hba1c}%"
        if profile.hba1c_date:
            hba1c_line += f" (measured {profile.hba1c_date})"
        else:
            hba1c_line += " (date of measurement unknown - treat as possibly outdated)"
        prompt += hba1c_line + "\n"

    if "complications" in relevant_categories and profile.complications:
        prompt += "Diabetes Complications: "
        prompt += ", ".join(profile.complications)
        prompt += "\n"

    # BMI calculation
    if "physical" in relevant_categories and profile.weight_kg and profile.height_cm and profile.height_cm > 0:
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

    if "physical" in relevant_categories:
        if profile.smoking_status == "Current":
            prompt += ("PATIENT SMOKES: Smoking sharply raises cardiovascular and "
                       "kidney risk in diabetes. Where relevant, gently encourage "
                       "quitting and mention local support exists - but do not lecture "
                       "or repeat it in every answer.\n")
        elif profile.smoking_status == "Former":
            prompt += "Patient is a former smoker - acknowledge this positively if it comes up.\n"

    prompt += "=====================================\n"

    # -------------------------------------------------
    # Glucose history
    # -------------------------------------------------

    if "glucose" in relevant_categories and profile.glucose_summary:
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

    # -------------------------------------------------
    # Remembered Facts From Past Conversations
    # -------------------------------------------------
    # ALWAYS included, never gated - these exist specifically so the model
    # doesn't re-ask something the patient already answered. Filtering them
    # by keyword would silently break that guarantee.

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
    # Follow-up Question Policy
    # -------------------------------------------------

    prompt += """

CLINICAL REASONING BEFORE ANSWERING

Before giving any personalized recommendation, silently assess:

STEP 1 - What would change my answer here?
List the clinical factors that genuinely affect this specific question.

STEP 2 - Which do I already know?
Check the patient profile, glucose history, remembered facts, and this
conversation. Anything already known must NEVER be asked again.

STEP 3 - Is anything missing that would change my recommendation?
If no - answer now, using the profile explicitly.
If yes - ask about it BEFORE answering.

ASKING FOLLOW-UP QUESTIONS

Default to asking when the answer genuinely depends on something you do not know.
Do not guess, and do not give generic advice as a substitute for asking.

Ask ONE question at a time, conversationally. Never send a list of questions.
Wait for the answer before asking the next.

Ask only what would genuinely change your advice. Two or three questions is the
normal maximum; if you want a fourth, answer with what you have and state what
it depends on.

Acknowledge what you already know when you ask, so the patient feels heard:
"I can see you are on Glucophage - what was your reading before this meal?"

If the patient does not answer or says they do not know, give the safest general
guidance and clearly state what it would depend on. Never stall.

WHAT NOT TO ASK

Never ask their diabetes type, medications, conditions, age, or recent glucose
readings - all of this is already above. Asking again looks careless.

EMERGENCY OVERRIDE

If their answers suggest an emergency - very low or very high glucose with
symptoms, confusion, vomiting, chest pain, breathing difficulty, or loss of
consciousness - STOP questioning immediately and tell them to seek urgent
medical care or call 1122. Safety before information gathering, always.

WORKED EXAMPLE

Patient asks: "Can I eat mango?"

Known: Type 2 diabetes, kidney disease, Glucophage, recent glucose data.
Genuinely missing: portion size, and their kidney stage - potassium limits
depend on it.

Good response: explain mango is high in natural sugar and moderate in potassium,
note the kidney disease makes the safe amount depend on their stage, then ask
ONE question - what stage their kidney disease is at - while offering general
portion guidance meanwhile.

Bad: a list of six questions before saying anything useful.
Bad: "Mango is fine in moderation" - ignores the kidney disease on file.
"""

    prompt += """

FOOD AND DIET QUESTIONS

For any question about eating a specific food:

Never give a simple yes or no.

Always structure the answer as:
1. Whether it can be eaten, with the specific portion limit
2. The reason, tied to this patient's own profile
3. Which of their conditions or medicines affect this
4. What to watch for afterwards

If the patient has kidney disease, heart disease, or high blood
pressure, you MUST mention how that condition specifically
affects this food before giving any portion advice.

If glucose control is poor (HbA1c above 9) you MUST state that
this food advice assumes improving control, and encourage
doctor review.

Never present a food as simply safe without naming the condition
or medication that makes it conditional.
"""

    # -------------------------------------------------
    # Examples
    # -------------------------------------------------

    prompt += """

Examples

If patient asks:

"What is diabetes?"

Answer immediately.

------------------------------------

If patient asks:

"My sugar is 6"

Ask

"Is that 6 mg/dL or 6 mmol/L?"

------------------------------------

If patient asks

"What fruits can I eat?"

Use diabetes type,
kidney disease,
heart disease,
and medications already stored.

Ask only if essential information is missing.

------------------------------------

If patient asks

"Can I exercise?"

Use existing profile.

Ask about insulin only if insulin information is missing.

------------------------------------

If patient asks

"How much insulin should I take?"

Never calculate insulin doses.

Recommend contacting the treating doctor.

"""

    # -------------------------------------------------
    # Disease-specific Guidance
    # -------------------------------------------------

    diabetes_type = profile.diabetes_type.strip().lower()
    if diabetes_type == "type1":
        prompt += """

TYPE 1 DIABETES

Focus on

- insulin

- hypoglycemia prevention

- carbohydrate counting

- sick-day rules

"""

    elif diabetes_type == "type2":
        prompt += """

TYPE 2 DIABETES

Focus on

- diet

- exercise

- weight management

- oral medicines

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

This user does not have diabetes.

They are using the app for general learning,
or to understand the condition for a family member.

Answer as a diabetes educator giving general information.

Do NOT address them as a patient.

Do NOT assume they have symptoms, medications, or complications.

Do NOT give them personal management advice.

If they ask about a specific person's care,
remind them that person should consult their own doctor.

"""

    # -------------------------------------------------
    # HbA1c
    # -------------------------------------------------

    if "hba1c" in relevant_categories and profile.hba1c is not None:

        if profile.hba1c >= 9:
            prompt += f"""

IMPORTANT

HbA1c is {profile.hba1c}%.

Encourage early doctor review.

"""

        elif profile.hba1c >= 7:
            prompt += f"""

HbA1c

Current HbA1c

{profile.hba1c}%

Encourage continued diabetes management.

"""

    # -------------------------------------------------
    # Other Conditions
    # -------------------------------------------------

    if "conditions" in relevant_categories:
        conditions = " ".join(profile.other_conditions).lower()

        if "kidney" in conditions:
            prompt += """

Kidney disease detected.

Avoid fixed protein recommendations.

Avoid potassium advice.

Recommend nephrologist or renal dietitian when appropriate.

"""

        if "heart" in conditions:
            prompt += """

Heart disease detected.

Recommend heart-healthy diet.

Avoid strenuous exercise advice.

"""

        if "blood pressure" in conditions:
            prompt += """

High blood pressure detected.

Recommend lower sodium diet.

Encourage regular BP monitoring.

"""

        if "pregnan" in conditions:
            prompt += """

Pregnancy detected.

Never recommend medication changes.

Encourage obstetric follow-up.

"""

    # -------------------------------------------------
    # Response Style
    # -------------------------------------------------

    if profile.response_style == "simple":
        prompt += """

Use

short sentences.

Simple English.

Maximum four short paragraphs.

"""

    else:
        prompt += """

Provide detailed explanations.

Use headings.

Explain step by step.

"""

    prompt += "\n========== END PROFILE ==========\n"

    return prompt

# =====================================================
# Fact Extraction Endpoint
# =====================================================

class ExtractRequest(BaseModel):
    conversation: list[dict] = Field(default_factory=list)
    existing_facts: list[str] = Field(default_factory=list)


@app.post("/api/v1/extract-facts")
async def extract_facts(request: ExtractRequest):

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
# Health Check
# =====================================================

@app.get("/health")
def health():
    return {
        "status": "running",
        "app": "GlycoAI",
        "version": "1.0",
        "language": "English / urdu"
    }


# =====================================================
# Chat Endpoint
# =====================================================

@app.post("/api/v1/chat")
async def chat(request: ChatRequest):

    # ---------------------------------------------
    # Safety Check
    # ---------------------------------------------

    safety = check_safety(request.message, request.profile.glucose_unit)

    if safety["blocked"]:
        return {
            "response": safety["response"],
            "safety_triggered": True,
            "tier": "emergency"
        }

    # ---------------------------------------------
    # Minimum-necessary context: classify which profile
    # categories this message actually needs before building
    # the system prompt.
    # ---------------------------------------------

    relevant_categories = classify_relevant_categories(request.message)

    system_prompt = build_system_prompt(request.profile, relevant_categories)

    # Log only what's needed to debug relevance decisions - not the
    # full profile. The old `print(request.profile)` was writing PHI
    # (allergies, HbA1c, medications) into Railway's server logs on
    # every message; this replaces it with a non-identifying summary.
    print(f"=== CATEGORIES INCLUDED: {sorted(relevant_categories) or ['none - base profile only']} ===")

    # Keep only the last few messages

    MAX_HISTORY = 6

    messages = request.conversation_history[-MAX_HISTORY:]

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

            max_tokens=600,

            temperature=0.3,

            system=system_prompt,

            messages=messages

        )

        return {

            "response": response.content[0].text,

            "safety_triggered": False,

            "tier": "education"

        }

    except Exception as e:
        print("CHAT ERROR:", e)

        return {
            "response": "Sorry, I could not process that right now. Please try again in a moment.",
            "safety_triggered": False,
            "tier": "education"
        }