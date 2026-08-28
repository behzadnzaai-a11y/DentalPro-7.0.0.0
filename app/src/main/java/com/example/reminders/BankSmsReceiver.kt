package com.example.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.util.BankSmsEngine
import com.example.util.PersianNumberFormatter

/**
 * دریافت‌کننده زنده پیامک‌های بانکی دریافتی گوشی (SMS Broadcast Receiver)
 * ۱) استخراج هوشمند تراکنش با موتور امتیازدهی ۵ بخشی (Date, Time, Type, Amount, Account)
 * ۲) ارسال نوتیفیکیشن با اولویت بالا — لمس نوتیفیکیشن دیالوگ ثبت در حسابداری را ۱ کلیکی باز می‌کند
 * ۳) اگر تراکنش به هیچ حساب شناخته‌شده‌ای متصل نشود، در صف «پیامک‌های ثبت‌نشده»
 *    (UnregisteredBankSms) درج می‌شود تا بعداً به‌صورت گروهی تخصیص یابد.
 */
class BankSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val senderAddress = messages.firstOrNull()?.originatingAddress ?: ""
            val fullSmsText = messages.joinToString("") { it.displayMessageBody ?: "" }
            if (fullSmsText.isBlank()) {
                pendingResult.finish()
                return
            }

            // فیلتر اولیه بر اساس فرستنده یا نام برند در متن پیامک: هماهنگ با
            // scanHistoricalSmsBackfill (که این چک را قبل از evaluateSms انجام می‌دهد)، تا
            // کاهش آستانه‌ی امتیازدهی باعث false-positive روی پیامک‌های غیربانکی نشود.
            // isBankSenderOrText (به‌جای isBankSender تنها) استفاده می‌شود چون فیلد sender
            // برخی پیامک‌های بلوبانک با هیچ‌کدام از مقادیر ثبت‌شده مطابقت ندارد، در حالی که
            // خودِ متن پیامک با نام برند («بلو») شروع می‌شود.
            if (senderAddress.isNotBlank() && !BankSmsEngine.isBankSenderOrText(senderAddress, fullSmsText)) {
                pendingResult.finish()
                return
            }

            // موتور هوشمند استخراج (نرمال‌سازی + فیلتر رمز/کارمزد + امتیازدهی ۵ بخشی)
            val candidate = BankSmsEngine.evaluateSms(fullSmsText, senderAddress)

            if (candidate != null && candidate.amountTomans > 0) {
                Log.d("BankSmsReceiver", "تراکنش پیامک بانکی کشف شد: ${candidate.bankName} - ${candidate.amountTomans} تومان - نوع: ${candidate.transactionType}")

                val formattedAmount = PersianNumberFormatter.formatAmount(candidate.amountTomans)
                val notificationId = ("bank_sms_" + System.currentTimeMillis()).hashCode()

                showBankSmsNotification(
                    context = context,
                    notificationId = notificationId,
                    bankName = candidate.bankName,
                    transactionType = candidate.transactionType,
                    amountText = formattedAmount,
                    dateStr = candidate.dateStr,
                    timeStr = candidate.timeStr,
                    rawSmsText = fullSmsText
                )

                // کارِ دیتابیس خارج از ترده اصلی (BroadcastReceiver باید سریع پایان یابد)
                Thread {
                    try {
                        val db = com.example.data.AppDatabase.getDatabase(context.applicationContext)
                        val repo = com.example.data.DentalRepository(db)
                        kotlinx.coroutines.runBlocking {
                            // بازتعریف صف «پیامک‌های ثبت‌نشده»: هر تراکنش پیامکی بانکی تا زمانی که
                            // در حسابداری ثبت (یا نادیده گرفته) نشود، در این صف لیست می‌ماند و لمس
                            // هر آیتم، همان پاپ‌آپ ثبت تراکنشِ نوتیفیکیشن را باز می‌کند. پس همیشه
                            // (با dedup روی smsNativeId) درج می‌شود — پس از ثبت از طریق پاپ‌آپ،
                            // وضعیت آیتم به ASSIGNED تغییر می‌کند و از صف خارج می‌شود.
                            // درج از مسیر Repository انجام می‌شود تا در صف سینک رئال‌تایم سرور قرار گیرد.
                            val hash = (candidate.rawSms + "|" + candidate.amountTomans + "|" + candidate.dateStr + "|" + candidate.timeStr).hashCode()
                            val existing = db.unregisteredBankSmsDao().getByNativeId("live_$hash")
                                ?: db.unregisteredBankSmsDao().getByNativeId("auto_$hash")
                                ?: db.unregisteredBankSmsDao().getByNativeId("manual_$hash")
                            if (existing == null) {
                                repo.upsertUnregisteredBankSms(
                                    com.example.data.UnregisteredBankSms(
                                        smsNativeId = "live_$hash",
                                        sender = senderAddress,
                                        content = candidate.rawSms,
                                        bankNameGuess = candidate.bankName,
                                        type = candidate.transactionType,
                                        amountTomans = candidate.amountTomans,
                                        accountNumberRaw = candidate.accountNumberRaw,
                                        dateStr = candidate.dateStr,
                                        timeStr = candidate.timeStr,
                                        score = candidate.score
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("BankSmsReceiver", "خطا در صف‌کردن پیامک بانکی: ${e.message}", e)
                    } finally {
                        pendingResult.finish()
                    }
                }.start()
                return // pendingResult توسط ترده بالا بسته می‌شود
            }
            pendingResult.finish()
        } catch (e: Exception) {
            Log.e("BankSmsReceiver", "خطا در تحلیل پیامک بانکی: ${e.message}", e)
            pendingResult.finish()
        }
    }
}