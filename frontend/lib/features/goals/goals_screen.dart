import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/format.dart';
import '../../l10n/app_localizations.dart';
import '../../theme/baseerah_theme.dart';
import 'state/goals_providers.dart';
import 'widgets/challenge_card.dart';
import 'widgets/reward_points_card.dart';

/// Consumer **Goals** screen (DESIGN §7.4, FR-09/10). A title/subtitle header
/// over one of the controller's phases: *loading* / *error* (retry) / *ready*
/// (the gold points card + the live challenge list). All state lives in
/// [goalsControllerProvider]; this widget renders the current phase and forwards
/// claim taps to the controller, surfacing a snackbar if a claim is rejected.
class GoalsScreen extends ConsumerWidget {
  const GoalsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final state = ref.watch(goalsControllerProvider);

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
              l.goalsTitle,
              style: textTheme.headlineSmall?.copyWith(
                color: BaseerahTokens.darkText,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              l.goalsSubtitle,
              style: textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted),
            ),
            const SizedBox(height: 18),
            Expanded(
              child: switch (state.phase) {
                GoalsPhase.loading =>
                  const Center(child: CircularProgressIndicator()),
                GoalsPhase.error => const _GoalsError(),
                GoalsPhase.ready => _ReadyView(state: state),
              },
            ),
          ],
        ),
      ),
    );
  }
}

/// Fetch-failed path — the shared load error message + a retry.
class _GoalsError extends ConsumerWidget {
  const _GoalsError();

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
            onPressed: () => ref.read(goalsControllerProvider.notifier).retry(),
            icon: const Icon(Icons.refresh),
            label: Text(l.retry),
          ),
        ],
      ),
    );
  }
}

/// Ready state: the gold points card pinned above a scrolling challenge list
/// (or a calm empty state when there are no challenges yet).
class _ReadyView extends ConsumerWidget {
  const _ReadyView({required this.state});

  final GoalsState state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final fmt = Fmt(Localizations.localeOf(context), l);

    Future<void> onClaim(String challengeId) async {
      final ok = await ref.read(goalsControllerProvider.notifier).claim(challengeId);
      if (!ok && context.mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text(l.goalsClaimError)));
      }
    }

    return ListView(
      padding: const EdgeInsets.only(bottom: 8),
      children: [
        RewardPointsCard(rewards: state.rewards!, fmt: fmt),
        const SizedBox(height: 22),
        Text(
          l.goalsChallengesHeader,
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
            color: BaseerahTokens.darkText,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 12),
        if (state.challenges.isEmpty)
          _EmptyChallenges(message: l.goalsEmpty)
        else
          for (final challenge in state.challenges)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: ChallengeCard(
                challenge: challenge,
                claiming: state.isClaiming(challenge.id),
                onClaim: () => onClaim(challenge.id),
                fmt: fmt,
              ),
            ),
      ],
    );
  }
}

/// Calm empty state when the persona has no active challenges.
class _EmptyChallenges extends StatelessWidget {
  const _EmptyChallenges({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 40),
      child: Column(
        children: [
          const Icon(Icons.flag_outlined, size: 44, color: BaseerahTokens.muted),
          const SizedBox(height: 12),
          Text(
            message,
            textAlign: TextAlign.center,
            style: textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted),
          ),
        ],
      ),
    );
  }
}
