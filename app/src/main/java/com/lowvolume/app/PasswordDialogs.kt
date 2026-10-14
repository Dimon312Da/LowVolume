package com.lowvolume.app

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout

private fun Activity.passwordField(hintRes: Int) = EditText(this).apply {
    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    setHint(hintRes)
    isSingleLine = true
}

private fun Activity.dialogContainer(vararg fields: EditText): LinearLayout {
    val padding = (resources.displayMetrics.density * 20).toInt()
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(padding, padding / 2, padding, 0)
        fields.forEach { addView(it) }
    }
}

private fun AlertDialog.showWithKeyboard(onPositive: () -> Unit) {
    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    show()
    // Переопределяем кнопку, чтобы диалог не закрывался при неверном вводе.
    getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { onPositive() }
}

private fun Activity.lockoutMessage(millisLeft: Long): String {
    val seconds = ((millisLeft + 999) / 1000).coerceAtLeast(1)
    return getString(R.string.password_locked_out, seconds)
}

/** Запрашивает текущий пароль и вызывает [onSuccess], если он верный. */
fun Activity.showEnterPasswordDialog(passwords: PasswordManager, onSuccess: () -> Unit) {
    val field = passwordField(R.string.password_hint)
    val locked = passwords.lockoutRemainingMillis()
    if (locked > 0) field.error = lockoutMessage(locked)

    val dialog = AlertDialog.Builder(this)
        .setTitle(R.string.enter_password_title)
        .setView(dialogContainer(field))
        .setPositiveButton(R.string.ok, null)
        .setNegativeButton(R.string.cancel, null)
        .create()

    dialog.showWithKeyboard {
        when (val result = passwords.verify(field.text.toString())) {
            PasswordManager.Result.Ok -> {
                dialog.dismiss()
                onSuccess()
            }
            is PasswordManager.Result.Wrong -> {
                field.text.clear()
                field.error = getString(R.string.password_wrong, result.attemptsLeft)
            }
            is PasswordManager.Result.LockedOut -> {
                field.text.clear()
                field.error = lockoutMessage(result.millisLeft)
            }
        }
    }
    field.requestFocus()
}

/** Запрашивает новый пароль с подтверждением и сохраняет его. */
fun Activity.showNewPasswordDialog(
    passwords: PasswordManager,
    cancelable: Boolean,
    onSet: () -> Unit,
) {
    val first = passwordField(R.string.new_password_hint)
    val second = passwordField(R.string.repeat_password_hint)

    val builder = AlertDialog.Builder(this)
        .setTitle(if (cancelable) R.string.change_password_title else R.string.create_password_title)
        .setMessage(R.string.create_password_message)
        .setView(dialogContainer(first, second))
        .setPositiveButton(R.string.save, null)
        .setCancelable(cancelable)
    if (cancelable) builder.setNegativeButton(R.string.cancel, null)
    val dialog = builder.create()
    dialog.setCanceledOnTouchOutside(cancelable)

    dialog.showWithKeyboard {
        val password = first.text.toString()
        when {
            password.length < PasswordManager.MIN_LENGTH ->
                first.error = getString(R.string.password_too_short, PasswordManager.MIN_LENGTH)
            password != second.text.toString() ->
                second.error = getString(R.string.password_mismatch)
            else -> {
                passwords.setPassword(password)
                dialog.dismiss()
                onSet()
            }
        }
    }
    first.requestFocus()
}
