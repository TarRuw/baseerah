import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format.dart';
import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../data/portfolio_models.dart';
import '../state/bank_providers.dart';
import 'monitoring_table.dart';

/// The Bank Portal Portfolio screen (DESIGN §7.6): four KPI cards over the live
/// `GET /bank/portfolio` figures, then the active-facility monitoring table.
/// Every figure is server-computed — this screen only formats and lays them out.
/// Loads/degrades on its own (spinner while fetching, inline retry on failure)
/// like the Applications queue, and is RTL-aware: the KPI [Row] and the table
/// both follow the ambient [Directionality].
class PortfolioScreen extends ConsumerWidget {
  const PortfolioScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final portfolio = ref.watch(bankPortfolioProvider);

    return Padding(
      padding: const EdgeInsets.all(BaseerahTokens.screenPadding),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l.bankPortfolioTitle,
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
              color: BaseerahTokens.darkText,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            l.bankPortfolioSub,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: BaseerahTokens.muted,
            ),
          ),
          const SizedBox(height: 20),
          Expanded(
            child: portfolio.when(
              skipLoadingOnReload: true,
              data: (p) => _PortfolioBody(portfolio: p),
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (_, __) => _PortfolioError(
                l: l,
                onRetry: () => ref.invalidate(bankPortfolioProvider),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// KPI cards + monitoring table, scrollable so the table never overflows on a
/// short window.
class _PortfolioBody extends StatelessWidget {
  const _PortfolioBody({required this.portfolio});

  final Portfolio portfolio;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final fmt = Fmt(Localizations.localeOf(context), l);
    final delta = portfolio.nplBaselineDelta;
    // A reduction (≤ 0) is the good outcome — colour it green, else neutral.
    final deltaColor =
        delta <= 0 ? BaseerahTokens.successGreen : BaseerahTokens.muted;

    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _KpiRow(
            cards: [
              _KpiCard(
                label: l.bankKpiActiveFacilities,
                value: fmt.fmt(portfolio.activeFacilities),
                note: l.bankKpiActiveNote,
                valueColor: BaseerahTokens.darkText,
              ),
              _KpiCard(
                label: l.bankKpiAvgStamina,
                value: fmt.fmt(portfolio.avgStamina),
                note: l.bankKpiStaminaNote,
                valueColor: BaseerahTokens.teal,
              ),
              _KpiCard(
                label: l.bankKpiNplRate,
                value: fmt.percent(portfolio.nplRate),
                note: '${fmt.percent(delta)} ${l.bankVsBaseline}',
                noteColor: deltaColor,
                valueColor: BaseerahTokens.successGreen,
              ),
              _KpiCard(
                label: l.bankKpiAtRisk,
                value: fmt.fmt(portfolio.atRiskAccounts),
                note: l.bankKpiAtRiskNote,
                valueColor: BaseerahTokens.alertRed,
              ),
            ],
          ),
          const SizedBox(height: 20),
          MonitoringTable(rows: portfolio.monitoring),
        ],
      ),
    );
  }
}

/// Lays the four KPI cards out responsively: side-by-side when wide enough,
/// wrapping to two-per-row on a narrow window (RTL-aware via [Wrap]).
class _KpiRow extends StatelessWidget {
  const _KpiRow({required this.cards});

  final List<Widget> cards;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        const gap = 16.0;
        // Four across when each card gets ≥ 190 px, else two across.
        final perRow = constraints.maxWidth >= (190 * 4 + gap * 3) ? 4 : 2;
        final width = (constraints.maxWidth - gap * (perRow - 1)) / perRow;
        return Wrap(
          spacing: gap,
          runSpacing: gap,
          children: [
            for (final card in cards) SizedBox(width: width, child: card),
          ],
        );
      },
    );
  }
}

/// One KPI card: a small label, a large value, and a coloured note line — the
/// §7.6 stat cards, styled from the theme card tokens (no hardcoded colours).
class _KpiCard extends StatelessWidget {
  const _KpiCard({
    required this.label,
    required this.value,
    required this.note,
    required this.valueColor,
    this.noteColor,
  });

  final String label;
  final String value;
  final String note;
  final Color valueColor;
  final Color? noteColor;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
        boxShadow: BaseerahTokens.shadowSoft,
        border: Border.all(color: BaseerahTokens.darkText.withValues(alpha: 0.05)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: textTheme.labelMedium?.copyWith(
              color: BaseerahTokens.muted,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 10),
          Text(
            value,
            style: textTheme.headlineMedium?.copyWith(
              color: valueColor,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            note,
            style: textTheme.bodySmall?.copyWith(
              color: noteColor ?? BaseerahTokens.muted,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

/// Inline error + retry for a failed portfolio load — mirrors the queue error in
/// the Applications screen (never a stuck spinner or blank screen).
class _PortfolioError extends StatelessWidget {
  const _PortfolioError({required this.l, required this.onRetry});

  final AppLocalizations l;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.cloud_off, color: BaseerahTokens.muted, size: 40),
          const SizedBox(height: 12),
          Text(
            l.bankPortfolioError,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: BaseerahTokens.muted,
            ),
          ),
          const SizedBox(height: 12),
          OutlinedButton(onPressed: onRetry, child: Text(l.retry)),
        ],
      ),
    );
  }
}
