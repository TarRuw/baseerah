import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/app_localizations.dart';
import '../../theme/baseerah_theme.dart';
import 'data/auth_repository.dart';
import 'state/auth_providers.dart';

/// Stage 2 of the login flow (Step 9.5): OTP entry.
///
/// Renders the **body** of the login card for the OTP step — the chrome (brand
/// hero, responsive frame, language toggle) is owned by `LoginMobileScreen`,
/// which swaps this in once a code has been requested. On a successful verify it
/// does nothing visible: the [AuthController] flips to authenticated and the
/// router's redirect lands the user on their role's shell. A rejected code
/// ([InvalidOtpFailure]) shows a generic inline error and stays put.
class LoginOtpScreen extends ConsumerStatefulWidget {
  const LoginOtpScreen({
    super.key,
    required this.mobile,
    required this.onChangeMobile,
  });

  /// The mobile the code was sent to (echoed in the sub-heading, and the value
  /// verified against the entered OTP).
  final String mobile;

  /// Back to stage 1 (mobile entry) — clears the pending mobile in the parent.
  final VoidCallback onChangeMobile;

  @override
  ConsumerState<LoginOtpScreen> createState() => _LoginOtpScreenState();
}

class _LoginOtpScreenState extends ConsumerState<LoginOtpScreen> {
  final _formKey = GlobalKey<FormState>();
  final _otpController = TextEditingController();

  bool _submitting = false;
  bool _resending = false;

  /// Inline error under the field (wrong/expired code or a network failure);
  /// cleared on the next edit or submit.
  String? _error;

  @override
  void dispose() {
    _otpController.dispose();
    super.dispose();
  }

  Future<void> _verify() async {
    final l = AppLocalizations.of(context);
    if (!_formKey.currentState!.validate()) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      // On success the router redirect navigates; nothing to do here.
      await ref
          .read(authControllerProvider.notifier)
          .verifyOtp(widget.mobile, _otpController.text.trim());
    } on InvalidOtpFailure {
      if (!mounted) return;
      setState(() => _error = l.loginInvalidCode);
    } catch (_) {
      if (!mounted) return;
      setState(() => _error = l.loginNetworkError);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Future<void> _resend() async {
    final l = AppLocalizations.of(context);
    setState(() {
      _resending = true;
      _error = null;
    });
    try {
      await ref.read(authControllerProvider.notifier).requestOtp(widget.mobile);
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(l.loginResent)));
    } catch (_) {
      if (!mounted) return;
      setState(() => _error = l.loginNetworkError);
    } finally {
      if (mounted) setState(() => _resending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final busy = _submitting || _resending;

    return Form(
      key: _formKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            l.loginOtpTitle,
            style: textTheme.titleLarge?.copyWith(
              color: BaseerahTokens.darkText,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            l.loginOtpSentTo(widget.mobile),
            style: textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted),
          ),
          const SizedBox(height: BaseerahTokens.gap),
          TextFormField(
            controller: _otpController,
            enabled: !busy,
            autofocus: true,
            keyboardType: TextInputType.number,
            textInputAction: TextInputAction.done,
            // OTP digits read left-to-right regardless of UI language.
            textDirection: TextDirection.ltr,
            textAlign: TextAlign.center,
            maxLength: 6,
            style: textTheme.headlineSmall?.copyWith(letterSpacing: 8),
            inputFormatters: [
              FilteringTextInputFormatter.digitsOnly,
              LengthLimitingTextInputFormatter(6),
            ],
            decoration: InputDecoration(
              labelText: l.loginOtpLabel,
              counterText: '',
              prefixIcon: const Icon(Icons.password_outlined),
              errorText: _error,
            ),
            validator: (value) {
              final v = (value ?? '').trim();
              if (v.length != 6) return l.loginOtpRequired;
              return null;
            },
            onChanged: (_) {
              if (_error != null) setState(() => _error = null);
            },
            onFieldSubmitted: (_) => busy ? null : _verify(),
          ),
          const SizedBox(height: BaseerahTokens.gap),
          ElevatedButton(
            onPressed: busy ? null : _verify,
            style: ElevatedButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 14),
            ),
            child: _submitting
                ? const SizedBox(
                    height: 20,
                    width: 20,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : Text(l.loginVerify),
          ),
          const SizedBox(height: 4),
          Row(
            children: [
              Expanded(
                child: Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: TextButton(
                    onPressed: busy ? null : widget.onChangeMobile,
                    child: Text(
                      l.loginChangeMobile,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ),
              ),
              Expanded(
                child: Align(
                  alignment: AlignmentDirectional.centerEnd,
                  child: TextButton(
                    onPressed: busy ? null : _resend,
                    child: Text(
                      l.loginResend,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
