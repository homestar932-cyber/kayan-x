# Kayan X 2.0 — Local Cognitive Agent

وكيل محلي مخصص لـ Termux/Android. التصميم يفصل بين:
- LLM: الفهم والتخطيط واختيار الخطوة التالية.
- Python: الحالة، السياسات، التحقق، وإدارة التنفيذ.
- Tools: العمليات الحقيقية على الملفات.

## الوصول إلى Download على الهاتف

بعد تثبيت Termux:API/Termux حسب بيئتك، نفّذ مرة واحدة:

```bash
termux-setup-storage
```

وافق على إذن التخزين. سيظهر مجلد:
`~/storage/downloads`

Kayan X يكتشفه تلقائيًا ويعرّف alias باسم `DOWNLOADS`.

مهم: Kayan X لا يفترض أن `/storage/emulated/0/Download` متاح مباشرة من Termux؛ المسار الموصى به هو `~/storage/downloads` بعد منح الإذن.

## التشغيل

```bash
cd ~/kayan-x
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# شغّل llama-server في جلسة أخرى، مثلًا على 127.0.0.1:8080
python -m kayan.main
```

## مثال

```text
أنت: اعرض الملفات الموجودة في مجلد الداونلود
```

أو:

```text
أنت: أنشئ مجلدًا اسمه KayanTest داخل الداونلود واكتب فيه ملف hello.txt يحتوي مرحبا كيان
```

العمليات الخطرة مثل الحذف والكتابة فوق ملف موجود تتطلب تأكيدًا.

## بنية المشروع

```text
kayan/
  core/        حلقة الوكيل والحالة والتخطيط والتنفيذ والتحقق
  llm/         عميل llama-server والمخرجات المنظمة
  tools/       أدوات الملفات والبحث
  safety/      حماية المسارات والسياسات والتأكيد
  memory/      ذاكرة المهمة والتفضيلات
  context/     اختيار الأدوات وإدارة السياق
  config.py
  main.py
```

## فلسفة Kayan X

Kayan X لا ينفذ خطة ثابتة طويلة بشكل أعمى. في كل دورة:
1. يفهم الهدف.
2. يختار خطوة واحدة.
3. يفحص السياسة.
4. ينفذ الأداة.
5. يسجل النتيجة.
6. يتحقق منها برمجيًا عندما يمكن.
7. يعيد التخطيط اعتمادًا على الحالة الجديدة.
8. يتوقف عند اكتمال الهدف أو عند الحاجة إلى المستخدم.

## GitHub

```bash
git init
git add .
git commit -m "Kayan X 2.0 initial architecture"
git branch -M main
git remote add origin https://github.com/USERNAME/kayan-x.git
git push -u origin main
```
