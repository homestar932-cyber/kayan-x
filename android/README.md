# Kayan X — تطبيق Android Native

تطبيق Android حقيقي (Kotlin + Jetpack Compose + NDK/llama.cpp) يعمل محلياً بدون إنترنت.

## ما هو هذا التطبيق؟

وكيل ملفات ذكي محلي يعمل على الهاتف:
1. يمنح المستخدم صلاحية Downloads عبر SAF (مرة واحدة)
2. يختار ملف GGUF من الجهاز
3. يحمّل النموذج محلياً (Auto Profiler يحدد n_gpu_layers)
4. ينفّذ مهام ملفات متعددة الخطوات مع تحقق حتمي

## المتطلبات

- Android Studio Ladybug أو أحدث
- NDK 26+
- CMake 3.22+
- جهاز/محاكي arm64-v8a (Android 8.0+)

## الإعداد والبناء

```bash
cd android
chmod +x scripts/setup_llama.sh
./scripts/setup_llama.sh          # يجلب llama.cpp

# افتح مجلد android/ في Android Studio
# أو من الطرفية:
./gradlew :app:assembleDebug
```

## التشغيل على الجهاز

1. ثبّت الـAPK
2. اضغط **منح Downloads** واختر مجلد التحميلات
3. اضغط **اختر GGUF** وحدّد نموذجك (مثلاً 1.5B أو 3B)
4. انتظر التحميل + Auto Profiler
5. اكتب هدفاً واضغط **تشغيل الوكيل**

## المعمارية

```
UI (Compose)
    ↓
Agent Loop (Planner → Policy → Execute → Verify → Re-plan)
    ↓
FileBridge (DocumentId فقط — لا مسارات خام)
    ↓
SAF + Persisted URI permissions
    ↓
Native Engine (llama.cpp عبر NDK/JNI)
```

## ملاحظات مهمة

- النموذج **خارج** الـAPK دائماً
- لا يُستخدم `MANAGE_EXTERNAL_STORAGE`
- لا مسارات `/sdcard` ثابتة
- `n_gpu_layers` يحدده Auto Profiler (مع إمكانية override لاحقاً)
- العمليات الخطرة تتطلب تأكيد المستخدم

## الحجم

بعد أول `assembleRelease` قِس الحجم الفعلي بـ:

```bash
apkanalyzer apk summary app/build/outputs/apk/release/*.apk
```

أبلغ عن: حجم DEX+resources، حجم المكتبات الأصلية، حجم النموذج (منفصل).
