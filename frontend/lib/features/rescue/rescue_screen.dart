import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/format.dart';
import '../../l10n/app_localizations.dart';
import '../../theme/baseerah_theme.dart';
import 'state/rescue_providers.dart';
import 'widgets/bridge_card.dart';
import 'widgets/recovery_gauge.dart';
import 'widgets/shortfall_banner.dart';

/// Consumer **Rescue** screen (DESIGN §7.3, FR-06/07). A title/subtitle header
/// over one of the state machine's phases: *loading* / *error* / *no-deficit*
/// (calm) / *open* (shortfall banner + selectable bridge cards + confirm) /
/// *complete* (success + before→after recovery + run-again). All state lives in
/// [rescueControllerProvider]; this widget only renders the current phase.
class RescueScreen extends ConsumerWidget {
  const RescueScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final state = ref.watch(rescueControllerProvider);

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          BaseerahTokens.screenPadding,
          6,
          BaseerahTokens.screenPadding,
          BaseerahTokens.screenPadding,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              l.rescueTitle,
              style: textTheme.headlineSmall?.copyWith(
                color: BaseerahTokens.darkText,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              l.rescueSubtitle,
              style: textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted),
            ),
            const SizedBox(height: 18),
            Expanded(child: _body(state)),
          ],
        ),
      ),
    );
  }

  Widget _body(RescueScreenState state) {
    return switch (state.phase) {
      RescuePhase.loading => const Center(child: CircularProgressIndicator()),
      RescuePhase.error => const _RescueError(),
      RescuePhase.noDeficit => const _NoDeficitView(),
      RescuePhase.open ||
      RescuePhase.confirming => _OpenView(state: state),
      RescuePhase.complete => _CompleteView(state: state),
    };
  }
}

/// Fetch-failed path — the shared load error message + a retry.
class _RescueError extends ConsumerWidget {
  const _RescueError();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.cloud_off_rounded,
              size: 44, color: BaseerahTokens.muted),
          const SizedBox(height: 12),
          Text(
            l.loadError,
            style: textTheme.bodyLarge?.copyWith(color: BaseerahTokens.muted),
          ),
          const SizedBox(height: 16),
          ElevatedButton.icon(
            onPressed: () => ref.read(rescueControllerProvider.notifier).retry(),
            icon: const Icon(Icons.refresh),
            label: Text(l.retry),
          ),
        ],
      ),
    );
  }
}

/// Healthy persona: a calm, reassuring no-deficit state (not an error banner).
class _NoDeficitView extends StatelessWidget {
  const _NoDeficitView();

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 88,
            height: 88,
            decoration: BoxDecoration(
              color: BaseerahTokens.successGreen.withValues(alpha: 0.12),
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.verified_rounded,
              color: BaseerahTokens.successGreen,
              size: 48,
            ),
          ),
          const SizedBox(height: 20),
          Text(
            l.rescueNoDeficitTitle,
            style: textTheme.headlineSmall?.copyWith(
              color: BaseerahTokens.darkText,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 8),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24),
            child: Text(
              l.rescueNoDeficitBody,
              textAlign: TextAlign.center,
              style:
                  textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted),
            ),
          ),
        ],
      ),
    );
  }
}

/// Open state: shortfall banner, the two selectable bridge cards, and a confirm
/// button gated on a selection. During [RescuePhase.confirming] the cards lock
/// and the button shows progress.
class _OpenView extends ConsumerWidget {
  const _OpenView({required this.state});

  final RescueScreenState state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final fmt = Fmt(Localizations.localeOf(context), l);
    final assessment = state.assessment!;
    final confirming = state.phase == RescuePhase.confirming;

    Future<void> onConfirm() async {
      final ok = await ref.read(rescueControllerProvider.notifier).confirm();
      if (!ok && context.mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text(l.rescueConfirmError)));
      }
    }

    return ListView(
      padding: const EdgeInsets.only(bottom: 8),
      children: [
        ShortfallBanner(
          shortfall: assessment.shortfall ?? 0,
          days: assessment.deficitInDays ?? 0,
          pulse: assessment.alertRaised,
          fmt: fmt,
        ),
        const SizedBox(height: 22),
        Text(
          l.rescueChooseBridge,
          style: textTheme.titleMedium?.copyWith(
            color: BaseerahTokens.darkText,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 12),
        for (final option in assessment.options)
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: BridgeCard(
              option: option,
              selected: state.selected == option.type,
              onTap: confirming
                  ? () {}
                  : () => ref
                      .read(rescueControllerProvider.notifier)
                      .select(option.type),
              fmt: fmt,
            ),
          ),
        const SizedBox(height: 8),
        SizedBox(
          width: double.infinity,
          child: ElevatedButton(
            onPressed: (state.canConfirm && !confirming) ? onConfirm : null,
            style: ElevatedButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 15),
            ),
            child: confirming
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : Text(l.rescueConfirm),
          ),
        ),
      ],
    );
  }
}

/// Complete state: a success mark, the before→after recovery gauge, the
/// server message, and a run-again action that re-fetches the open state.
class _CompleteView extends ConsumerWidget {
  const _CompleteView({required this.state});

  final RescueScreenState state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final outcome = state.outcome!;

    return ListView(
      padding: const EdgeInsets.only(bottom: 8),
      children: [
        const SizedBox(height: 8),
        Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 72,
                height: 72,
                decoration: BoxDecoration(
                  color: BaseerahTokens.successGreen.withValues(alpha: 0.12),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.check_circle_rounded,
                  color: BaseerahTokens.successGreen,
                  size: 42,
                ),
              ),
              const SizedBox(height: 12),
              Text(
                l.rescueSuccessTitle,
                style: textTheme.headlineSmall?.copyWith(
                  color: BaseerahTokens.darkText,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 22),
        Text(
          l.rescueRecoveryLabel,
          style: textTheme.titleMedium?.copyWith(
            color: BaseerahTokens.darkText,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 8),
        Center(
          child: SizedBox(
            width: 230,
            child: RecoveryGauge(outcome: outcome),
          ),
        ),
        const SizedBox(height: 16),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          child: Text(
            outcome.message,
            textAlign: TextAlign.center,
            style: textTheme.bodyMedium?.copyWith(color: BaseerahTokens.darkText),
          ),
        ),
        const SizedBox(height: 24),
        SizedBox(
          width: double.infinity,
          child: OutlinedButton.icon(
            onPressed: () =>
                ref.read(rescueControllerProvider.notifier).runAgain(),
            icon: const Icon(Icons.refresh),
            label: Text(l.rescueRunAgain),
            style: OutlinedButton.styleFrom(
              foregroundColor: BaseerahTokens.teal,
              side: const BorderSide(color: BaseerahTokens.teal),
              padding: const EdgeInsets.symmetric(vertical: 15),
              shape: RoundedRectangleBorder(
                borderRadius:
                    BorderRadius.circular(BaseerahTokens.radiusControl),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
