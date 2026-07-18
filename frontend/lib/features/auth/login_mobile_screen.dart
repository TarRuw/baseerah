import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/app_localizations.dart';
import '../../l10n/locale_provider.dart';
import '../../shell/responsive_frame.dart';
import '../../theme/baseerah_theme.dart';
import 'login_otp_screen.dart';
import 'state/auth_providers.dart';

/// The `/login` route (Step 9.5) and stage 1 (mobile entry) of the two-stage
/// phone → OTP sign-in.
///
/// Owns the login chrome — the dark-green brand hero, the language toggle, and
/// the phone-shaped [ResponsiveFrame] (so it reads like the consumer shell on
/// wide web) — and swaps its card body between the mobile form and
/// [LoginOtpScreen] once a code has been requested. Arabic-first/RTL, themed
/// entirely from [BaseerahTokens]. Navigation on success is handled by the
/// router redirect, not here.
class LoginMobileScreen extends ConsumerStatefulWidget {
  const LoginMobileScreen({super.key});

  @override
  ConsumerState<LoginMobileScreen> createState() => _LoginMobileScreenState();
}

class _LoginMobileScreenState extends ConsumerState<LoginMobileScreen> {
  final _formKey = GlobalKey<FormState>();
  final _mobileController = TextEditingController();

  /// The mobile a code was successfully requested for — non-null once we advance
  /// to the OTP stage. Null shows the mobile-entry form.
  String? _pendingMobile;

  bool _submitting = false;
  String? _error;

  /// Backend contract (Step 9.2 `OtpRequest`): `+` then 8–15 digits.
  static final _mobilePattern = RegExp(r'^\+[0-9]{8,15}$');

  @override
  void dispose() {
    _mobileController.dispose();
    super.dispose();
  }

  Future<void> _sendCode() async {
    final l = AppLocalizations.of(context);
    if (!_formKey.currentState!.validate()) return;
    FocusScope.of(context).unfocus();
    final mobile = _mobileController.text.trim();
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      // Request is intentionally generic (no user enumeration): success just
      // advances to OTP entry; a real send is mocked in the backend.
      await ref.read(authControllerProvider.notifier).requestOtp(mobile);
      if (!mounted) return;
      setState(() => _pendingMobile = mobile);
    } catch (_) {
      if (!mounted) return;
      setState(() => _error = l.loginNetworkError);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);

    return Scaffold(
      body: SafeArea(
        child: ResponsiveFrame(
          maxWidth: BaseerahTokens.phoneFrameMaxWidth,
          child: Padding(
            padding: const EdgeInsets.all(BaseerahTokens.screenPadding),
            child: Column(
              children: [
                Align(
                  alignment: AlignmentDirectional.centerEnd,
                  child: TextButton(
                    onPressed: () => ref.read(localeProvider.notifier).toggle(),
                    child: Text(l.languageToggle),
                  ),
                ),
                Expanded(
                  child: SingleChildScrollView(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const SizedBox(height: 8),
                        const _BrandHero(),
                        const SizedBox(height: 28),
                        Container(
                          padding: const EdgeInsets.all(20),
                          decoration: BoxDecoration(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(
                              BaseerahTokens.radiusCard,
                            ),
                            boxShadow: BaseerahTokens.shadowSoft,
                          ),
                          child: _pendingMobile == null
                              ? _buildMobileForm(context, l)
                              : LoginOtpScreen(
                                  mobile: _pendingMobile!,
                                  onChangeMobile: () =>
                                      setState(() => _pendingMobile = null),
                                ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildMobileForm(BuildContext context, AppLocalizations l) {
    final textTheme = Theme.of(context).textTheme;
    return Form(
      key: _formKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            l.loginMobileTitle,
            style: textTheme.titleLarge?.copyWith(
              color: BaseerahTokens.darkText,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            l.loginMobileSubtitle,
            style: textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted),
          ),
          const SizedBox(height: BaseerahTokens.gap),
          TextFormField(
            controller: _mobileController,
            enabled: !_submitting,
            autofocus: true,
            keyboardType: TextInputType.phone,
            textInputAction: TextInputAction.done,
            // Phone numbers read left-to-right regardless of UI language.
            textDirection: TextDirection.ltr,
            inputFormatters: [
              FilteringTextInputFormatter.allow(RegExp(r'[0-9+]')),
            ],
            decoration: InputDecoration(
              labelText: l.loginMobileLabel,
              hintText: l.loginMobileHint,
              hintTextDirection: TextDirection.ltr,
              prefixIcon: const Icon(Icons.phone_outlined),
              errorText: _error,
            ),
            validator: (value) {
              final v = (value ?? '').trim();
              if (!_mobilePattern.hasMatch(v)) return l.loginMobileInvalid;
              return null;
            },
            onChanged: (_) {
              if (_error != null) setState(() => _error = null);
            },
            onFieldSubmitted: (_) => _submitting ? null : _sendCode(),
          ),
          const SizedBox(height: BaseerahTokens.gap),
          ElevatedButton(
            onPressed: _submitting ? null : _sendCode,
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
                : Text(l.loginSendCode),
          ),
        ],
      ),
    );
  }
}

/// The dark-green brand hero atop the login card: logo mark, wordmark, tagline.
class _BrandHero extends StatelessWidget {
  const _BrandHero();

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    return Column(
      children: [
        Container(
          width: 76,
          height: 76,
          decoration: BoxDecoration(
            gradient: BaseerahTokens.tealGradient,
            borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
            boxShadow: BaseerahTokens.shadowMedium,
          ),
          child: const Icon(
            Icons.visibility_outlined,
            color: Colors.white,
            size: 38,
          ),
        ),
        const SizedBox(height: 16),
        Text(
          l.appTitle,
          style: textTheme.headlineMedium?.copyWith(
            color: BaseerahTokens.teal,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          l.loginTagline,
          textAlign: TextAlign.center,
          style: textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted),
        ),
      ],
    );
  }
}
