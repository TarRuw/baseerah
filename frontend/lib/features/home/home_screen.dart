import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/format.dart';
import '../../l10n/app_localizations.dart';
import '../../theme/baseerah_theme.dart';
import 'data/account.dart';
import 'data/client.dart';
import 'data/stress_score.dart';
import 'state/home_providers.dart';
import 'widgets/deficit_warning_card.dart';
import 'widgets/forecast_chart.dart';
import 'widgets/linked_accounts_list.dart';
import 'widgets/stat_card.dart';
import 'widgets/stress_gauge.dart';

/// Consumer Home / Radar screen (DESIGN §7.1): greeting + avatar, live-multibank
/// badge, animated Stress Score gauge, (conditional) pulsing deficit warning,
/// income-consistency & spending-velocity stat cards, 30-day forecast slot, and
/// the linked-accounts list. Every value comes from the API — nothing hardcoded.
///
/// The screen degrades per section: a failed score call shows a retry in the
/// gauge slot without taking down the accounts list, and vice-versa.
class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final fmt = Fmt(Localizations.localeOf(context), l);

    final client = ref.watch(currentClientProvider);
    final score = ref.watch(stressScoreProvider);
    final accounts = ref.watch(accountsProvider);
    final deficit = ref.watch(deficitSignalProvider);

    return SafeArea(
      child: RefreshIndicator(
        onRefresh: () async => ref.invalidate(currentClientProvider),
        child: ListView(
          padding: const EdgeInsets.all(BaseerahTokens.screenPadding),
          children: [
            _Header(client: client, accounts: accounts),
            const SizedBox(height: BaseerahTokens.gap),

            // ── Gauge ──────────────────────────────────────────────────────
            score.when(
              data: (s) => Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24),
                child: StressGauge(
                  score: s.score,
                  color: s.color,
                  zoneLabel: _zoneLabel(l, s.zone),
                  caption: l.outOf100,
                ),
              ),
              loading: () => const _SectionLoading(height: 220),
              error: (_, __) => _SectionError(
                l: l,
                onRetry: () => ref.invalidate(currentClientProvider),
              ),
            ),
            const SizedBox(height: BaseerahTokens.gap),

            // ── Deficit warning (hidden until a signal exists — Phase 3) ────
            if (deficit != null) ...[
              DeficitWarningCard(signal: deficit, fmt: fmt),
              const SizedBox(height: BaseerahTokens.gap),
            ],

            // ── Sub-score stat cards ───────────────────────────────────────
            score.maybeWhen(
              data: (s) => Row(
                children: [
                  Expanded(
                    child: StatCard(
                      label: l.incomeConsistency,
                      value: s.incomeConsistency,
                      icon: Icons.trending_up,
                      accent: BaseerahTokens.teal,
                    ),
                  ),
                  const SizedBox(width: BaseerahTokens.gap),
                  Expanded(
                    child: StatCard(
                      label: l.spendingVelocity,
                      value: s.spendingVelocity,
                      icon: Icons.speed,
                      accent: BaseerahTokens.gold,
                    ),
                  ),
                ],
              ),
              orElse: () => const SizedBox.shrink(),
            ),
            const SizedBox(height: BaseerahTokens.gap),

            // ── Forecast chart (Phase 3 Step 3.2) ──────────────────────────
            const ForecastChart(),
            const SizedBox(height: BaseerahTokens.gap),

            // ── Linked accounts ────────────────────────────────────────────
            Text(
              l.linkedAccounts,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                color: BaseerahTokens.darkText,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 8),
            accounts.when(
              data: (list) => LinkedAccountsList(accounts: list, fmt: fmt),
              loading: () => const _SectionLoading(height: 120),
              error: (_, __) => _SectionError(
                l: l,
                onRetry: () => ref.invalidate(currentClientProvider),
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _zoneLabel(AppLocalizations l, StressZone zone) => switch (zone) {
    StressZone.optimal => l.zoneOptimal,
    StressZone.warning => l.zoneWarning,
    StressZone.critical => l.zoneCritical,
  };
}

/// Greeting + avatar + live-multibank badge. The badge counts distinct banks
/// from the accounts payload once loaded.
class _Header extends StatelessWidget {
  const _Header({required this.client, required this.accounts});

  final AsyncValue<Client> client;
  final AsyncValue<List<Account>> accounts;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final bankCount = accounts.maybeWhen(
      data: (list) => list.map((a) => a.bankName).toSet().length,
      orElse: () => 0,
    );

    return Row(
      children: [
        Container(
          width: 48,
          height: 48,
          decoration: const BoxDecoration(
            gradient: BaseerahTokens.tealGradient,
            shape: BoxShape.circle,
          ),
          child: const Icon(Icons.person, color: Colors.white),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                l.homeGreeting,
                style: textTheme.bodyMedium?.copyWith(
                  color: BaseerahTokens.muted,
                ),
              ),
              Text(
                client.maybeWhen(
                  data: (c) => c.profileLabel,
                  orElse: () => l.appTitle,
                ),
                style: textTheme.titleMedium?.copyWith(
                  color: BaseerahTokens.darkText,
                  fontWeight: FontWeight.w700,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ),
        if (bankCount > 0)
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: BaseerahTokens.successGreen.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(BaseerahTokens.radiusControl),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 8,
                  height: 8,
                  decoration: const BoxDecoration(
                    color: BaseerahTokens.successGreen,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 6),
                Text(
                  l.liveBanks(bankCount),
                  style: textTheme.labelMedium?.copyWith(
                    color: BaseerahTokens.successGreen,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}

/// Neutral fixed-height shimmer stand-in while a section loads.
class _SectionLoading extends StatelessWidget {
  const _SectionLoading({required this.height});

  final double height;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: height,
      child: const Center(child: CircularProgressIndicator()),
    );
  }
}

/// Inline error with retry — keeps the rest of Home usable if one call fails.
class _SectionError extends StatelessWidget {
  const _SectionError({required this.l, required this.onRetry});

  final AppLocalizations l;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
        boxShadow: BaseerahTokens.shadowSoft,
      ),
      child: Column(
        children: [
          const Icon(
            Icons.cloud_off_outlined,
            color: BaseerahTokens.muted,
            size: 28,
          ),
          const SizedBox(height: 8),
          Text(
            l.loadError,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: BaseerahTokens.muted,
            ),
          ),
          const SizedBox(height: 8),
          OutlinedButton(onPressed: onRetry, child: Text(l.retry)),
        ],
      ),
    );
  }
}
