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
    version="1.1"
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

EMERGENCY_WORDS = [
    "unconscious",
    "passed out",
    "not breathing",
    "can't breathe",
    "cant breathe",
    "difficulty breathing",
    "seizure",
    "fitting",
    "stroke",
    "heart attack",
    "chest pain",
    "collapse",
    "collapsed",
    # Roman Urdu
    "behosh",
    "be hosh",
    "behoshi",
    "saans nahi",
    "dora par",
    "gir gaya",
    "gir gayi",
]

DOSING_WORDS = [
    "how many units",
    "how much insulin",
    "increase insulin",
    "reduce insulin",
    "insulin dose",
    "change insulin",
    "adjust insulin",
    "stop taking",
    "should i stop",
    "double my dose",
    "skip my dose",
    # Roman Urdu
    "dose bata",
    "dose batao",
    "kitni insulin",
    "insulin kitni",
    "kitni units",
]

# Glucose values that are genuinely critical, matched as whole numbers so
# "sugar 300" is never mistaken for "sugar 30".
GLUCOSE_PATTERN = re.compile(
    r"(?:sugar|glucose|bsl|reading|sheeta|shugar)\D{0,12}(\d{1,4})(?!\d)(?:\.\d+)?",
    re.IGNORECASE,
)

EMERGENCY_RESPONSE = (
    "This may be a medical emergency. Please call your local emergency "
    "services (1122 in Pakistan) or go to the nearest hospital immediately.\n\n"
    "If the person is conscious and able to swallow, and their sugar is low, "
    "give them something sweet right away - juice, regular soft drink, or "
    "sugar dissolved in water. Do not give anything by mouth if they are "
    "drowsy or unconscious."
)

DOSING_RESPONSE = (
    "I cannot recommend or calculate insulin or medication doses - that has to "
    "come from your own doctor, because it depends on tests and details only "
    "they can assess.\n\n"
    "Please follow the instructions you were given, and contact your "
    "healthcare provider if you think something needs to change. If you are "
    "unwell right now and unsure what to do, treat it as urgent and seek care."
)


def check_safety(message: str, glucose_unit: str = "mg/dL"):
    """Hard safety gate that runs before the model sees anything."""

    message_lower = message.lower()

    for word in EMERGENCY_WORDS:
        if word in message_lower:
            return {"blocked": True, "response": EMERGENCY_RESPONSE}

    # Numeric glucose check, unit-aware and boundary-safe.
    for match in GLUCOSE_PATTERN.finditer(message_lower):
        try:
            value = int(match.group(1))
        except (TypeError, ValueError):
            continue

        if glucose_unit == "mmol/L":
            # mmol/L: below 3 or above 25 warrants urgent care
            if value <= 3 or value >= 25:
                return {"blocked": True, "response": EMERGENCY_RESPONSE}
        else:
            # mg/dL: a 1-2 digit number here is almost always mmol/L or a typo,
            # so only act on clearly critical mg/dL values.
            if 20 <= value <= 54 or value >= 450:
                return {"blocked": True, "response": EMERGENCY_RESPONSE}

    for word in DOSING_WORDS:
        if word in message_lower:
            return {"blocked": True, "response": DOSING_RESPONSE}

    return {"blocked": False, "response": None}


# =====================================================
# System Prompt Builder
# =====================================================

def build_system_prompt(profile: ProfileData) -> str:

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

    prompt += f"Insulin Type: {profile.insulin_type or 'Unknown'}\n"

    prompt += f"Glucose Unit: {profile.glucose_unit}\n"

    prompt += f"Current Medicines: {', '.join(profile.medications) if profile.medications else 'None'}\n"

    prompt += f"Glucose Monitoring: {profile.glucose_monitoring or 'Unknown'}\n"

    prompt += f"Previous Severe Hypoglycemia: {profile.severe_hypoglycemia or 'Unknown'}\n"

    # Conditions and complications come from one onboarding screen, so treat
    # them as a single combined list everywhere.
    all_conditions = [c for c in (profile.other_conditions + profile.complications) if c]

    prompt += f"Other Medical Conditions: {', '.join(all_conditions) if all_conditions else 'None'}\n"

    if profile.hba1c is not None:
        prompt += f"HbA1c: {profile.hba1c}%\n"
    else:
        prompt += "HbA1c: Not recorded\n"

    # BMI calculation
    if profile.weight_kg and profile.height_cm and profile.height_cm > 0:
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

    if profile.glucose_summary:
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
async def get_tips(request: TipsRequest):

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
        "version": "1.1",
        "language": "English / urdu"
    }


# =====================================================
# Chat Endpoint
# =====================================================

# Raised from 6: the gate asks follow-up questions, so the model needs to see
# enough turns to know what it already asked and what was answered.
MAX_HISTORY = 14


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
            "needs_context": False,
            "quick_replies": [],
            "tier": "emergency",
        }

    # ---------------------------------------------
    # Build Claude System Prompt
    # ---------------------------------------------

    system_prompt = build_system_prompt(request.profile)

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