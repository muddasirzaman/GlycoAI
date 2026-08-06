package com.sugarsaathi.app

import java.util.concurrent.TimeUnit

object DailyTips {

    private val tipsEn = listOf(
        // Monitoring
        "Check your blood sugar at the same times each day so you can compare readings fairly.",
        "Write down your readings, or log them here — patterns matter more than single numbers.",
        "Test before and two hours after a meal occasionally to learn how that meal affects you.",
        "Clean and dry your finger before testing; food residue can give a falsely high reading.",
        "Rotate which finger you prick to avoid soreness.",
        "If a reading seems surprising, wash your hands and test again before acting on it.",

        // Medication
        "Take your medicines at the same time every day — set a phone alarm if it helps.",
        "Never stop or change a medicine on your own, even if you feel well.",
        "Keep a list of your medicines in your wallet for emergencies.",
        "If you miss a dose, ask your doctor or pharmacist what to do — don't double up on your own.",
        "Refill your prescriptions a few days before they run out.",

        // Diet
        "Fill half your plate with vegetables, a quarter with protein, a quarter with rice or roti.",
        "Drink water instead of sugary drinks, juices, and soft drinks.",
        "Eat roti made with whole wheat atta rather than refined flour where you can.",
        "Fruit is healthy, but eat it whole rather than as juice — the fibre slows the sugar.",
        "Don't skip meals to lower your sugar; it often causes a bigger rise later.",
        "Watch portion sizes of rice, potatoes, and bread — these raise sugar the most.",
        "Nuts like almonds and walnuts make a better snack than biscuits.",
        "Reduce salt slowly — your taste adjusts within a few weeks.",
        "Dahi (yoghurt) without sugar is a good addition to meals.",
        "Be careful with 'sugar-free' sweets — they can still raise blood sugar.",
        "Chana, daal, and beans are filling and gentler on blood sugar than white rice.",

        // Exercise
        "A 20-minute walk after dinner can noticeably lower your sugar.",
        "Any movement counts — housework, gardening, and walking to the shop all help.",
        "Start slowly if you haven't exercised in a while; ten minutes is a fine beginning.",
        "Try to be active most days rather than doing a lot once a week.",
        "Wear comfortable shoes when walking to protect your feet.",
        "If you feel dizzy or unwell during exercise, stop and rest.",

        // Foot care
        "Check your feet every day for cuts, blisters, or colour changes.",
        "Dry between your toes carefully after washing.",
        "Never walk barefoot, even at home — small cuts can become serious.",
        "Cut toenails straight across, not into the corners.",
        "See a doctor promptly about any sore that isn't healing.",

        // Hypoglycemia
        "Keep something sweet with you — juice, glucose tablets, or sugar sachets.",
        "Shakiness, sweating, and sudden hunger can mean low blood sugar. Treat it straight away.",
        "After treating a low, wait 15 minutes and check again.",
        "Tell your family what low blood sugar looks like and how to help.",
        "Low sugar at night can cause bad dreams or morning headaches — mention it to your doctor.",

        // General health
        "Get your eyes checked once a year, even if your vision seems fine.",
        "Ask your doctor about your HbA1c — it shows your average over three months.",
        "Brush and floss daily; gum problems are more common with diabetes.",
        "If you smoke, quitting is the single biggest thing you can do for your health.",
        "Have your kidney function checked as your doctor recommends.",
        "Keep your vaccinations up to date — infections can raise blood sugar.",

        // Sick days
        "Keep taking your diabetes medicines when you're ill unless your doctor says otherwise.",
        "Check your sugar more often when you have a fever or infection.",
        "Drink plenty of water when unwell.",
        "Contact your doctor if you can't keep food or water down.",

        // Emotional
        "Diabetes is a long journey — a difficult week doesn't undo your progress.",
        "Talk to someone you trust if you feel overwhelmed; that's a normal part of this.",
        "Celebrate small wins, like a week of steady readings.",
        "Poor sleep makes blood sugar harder to control — aim for a regular bedtime.",
        "Stress raises blood sugar, so time spent relaxing is time well spent.",

        // Practical
        "Carry a card or note saying you have diabetes, in case of emergency.",
        "Store insulin as instructed and keep it out of direct sunlight.",
        "Take your glucometer to doctor appointments so they can see your readings.",
        "Write down questions before your appointment so you don't forget them.",
        "Ask your doctor to explain anything you don't understand — that's what they're there for.",
        "Keep emergency numbers saved in your phone, including 1122.",
        "During Ramadan, speak to your doctor before fasting — your medicines may need adjusting."
    )

    private val tipsUr = listOf(
        "اپنی شوگر روزانہ ایک ہی وقت پر چیک کریں تاکہ آپ ریڈنگز کا موازنہ کر سکیں۔",
        "اپنی ریڈنگز لکھیں یا یہاں محفوظ کریں — رجحان ایک ریڈنگ سے زیادہ اہم ہے۔",
        "کبھی کبھار کھانے سے پہلے اور دو گھنٹے بعد ٹیسٹ کریں تاکہ اثر معلوم ہو۔",
        "ٹیسٹ سے پہلے ہاتھ دھو کر خشک کریں، ورنہ ریڈنگ غلط آ سکتی ہے۔",
        "انگلیاں بدل بدل کر استعمال کریں تاکہ درد نہ ہو۔",
        "اپنی دوائیں روز ایک ہی وقت پر لیں — فون پر الارم لگا لیں۔",
        "اپنی مرضی سے دوا بند یا تبدیل نہ کریں، چاہے طبیعت ٹھیک ہو۔",
        "اپنی دواؤں کی فہرست ہمیشہ اپنے پاس رکھیں۔",
        "آدھی پلیٹ سبزی، چوتھائی پروٹین، چوتھائی چاول یا روٹی رکھیں۔",
        "میٹھے مشروبات اور جوس کی جگہ پانی پئیں۔",
        "میدے کی بجائے چکی کے آٹے کی روٹی بہتر ہے۔",
        "پھل جوس کی بجائے ثابت کھائیں — ریشہ شوگر کو آہستہ کرتا ہے۔",
        "شوگر کم کرنے کے لیے کھانا نہ چھوڑیں، بعد میں زیادہ بڑھ سکتی ہے۔",
        "چاول، آلو اور روٹی کی مقدار کا خیال رکھیں۔",
        "بسکٹ کی جگہ بادام یا اخروٹ بہتر ناشتہ ہیں۔",
        "بغیر چینی کے دہی کھانے میں شامل کریں۔",
        "چنے، دال اور لوبیا سفید چاول سے بہتر ہیں۔",
        "رات کے کھانے کے بعد بیس منٹ کی چہل قدمی شوگر کم کرتی ہے۔",
        "ہر حرکت فائدہ دیتی ہے — گھر کا کام بھی ورزش ہے۔",
        "اگر عرصے سے ورزش نہیں کی تو آہستہ شروع کریں۔",
        "چلتے وقت آرام دہ جوتے پہنیں۔",
        "روزانہ اپنے پاؤں دیکھیں — زخم یا چھالے تو نہیں۔",
        "نہانے کے بعد انگلیوں کے درمیان اچھی طرح خشک کریں۔",
        "گھر میں بھی ننگے پاؤں نہ چلیں۔",
        "ناخن سیدھے کاٹیں، کونوں سے نہیں۔",
        "کوئی زخم ٹھیک نہ ہو رہا ہو تو فوراً ڈاکٹر کو دکھائیں۔",
        "ہمیشہ کچھ میٹھا ساتھ رکھیں — جوس یا گلوکوز کی گولیاں۔",
        "کپکپاہٹ، پسینہ اور اچانک بھوک شوگر کم ہونے کی علامت ہے۔",
        "شوگر کم ہونے کے علاج کے پندرہ منٹ بعد دوبارہ چیک کریں۔",
        "گھر والوں کو بتائیں کہ شوگر کم ہونے پر کیا کرنا ہے۔",
        "سال میں ایک بار آنکھوں کا معائنہ ضرور کروائیں۔",
        "اپنے ڈاکٹر سے HbA1c کے بارے میں پوچھیں۔",
        "روزانہ دانت صاف کریں — ذیابیطس میں مسوڑھوں کے مسائل عام ہیں۔",
        "اگر سگریٹ پیتے ہیں تو چھوڑنا سب سے بڑا فائدہ دے گا۔",
        "گردوں کا ٹیسٹ ڈاکٹر کے مشورے کے مطابق کرواتے رہیں۔",
        "بیماری میں دوائیں جاری رکھیں جب تک ڈاکٹر منع نہ کرے۔",
        "بخار یا انفیکشن میں شوگر زیادہ بار چیک کریں۔",
        "طبیعت خراب ہو تو پانی زیادہ پئیں۔",
        "اگر کھانا پانی نہ رک رہا ہو تو ڈاکٹر سے رابطہ کریں۔",
        "ذیابیطس ایک لمبا سفر ہے — ایک مشکل ہفتہ سب ختم نہیں کرتا۔",
        "پریشانی محسوس ہو تو کسی قریبی سے بات کریں۔",
        "چھوٹی کامیابیوں کو بھی سراہیں۔",
        "نیند پوری کریں — کم نیند شوگر بڑھاتی ہے۔",
        "ذہنی دباؤ شوگر بڑھاتا ہے، سکون کا وقت ضروری ہے۔",
        "ہنگامی حالت کے لیے ذیابیطس کا کارڈ ساتھ رکھیں۔",
        "انسولین کو دھوپ سے بچا کر رکھیں۔",
        "ڈاکٹر کے پاس اپنا گلوکومیٹر ساتھ لے جائیں۔",
        "ملاقات سے پہلے سوالات لکھ لیں۔",
        "جو بات سمجھ نہ آئے، ڈاکٹر سے ضرور پوچھیں۔",
        "ہنگامی نمبر فون میں محفوظ رکھیں، بشمول 1122۔",
        "رمضان میں روزے سے پہلے ڈاکٹر سے مشورہ ضرور کریں۔"
    )

    // Same tips all day, new set tomorrow. Cycles without repeating.
    fun todaysTips(language: String, count: Int = 3): List<String> {
        val list = if (language == "ur") tipsUr else tipsEn
        if (list.isEmpty()) return emptyList()

        val day = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis()).toInt()
        val start = (day * count) % list.size

        return (0 until count).map { list[(start + it) % list.size] }
    }
}