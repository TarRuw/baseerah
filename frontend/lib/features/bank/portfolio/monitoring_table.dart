import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../data/portfolio_models.dart';

/// The active-facility monitoring table (DESIGN §7.6): one row per active-book
/// facility with borrower, facility descriptor, a health score + trend arrow,
/// and a colour-coded status badge (Healthy / Watch / At-risk). Laid out with
/// flexible columns inside a card; RTL-aware — the header/data [Row]s follow the
/// ambient [Directionality] and the trend arrows mirror under Arabic.
class MonitoringTable extends StatelessWidget {
  const MonitoringTable({super.key, required this.rows});

  final List<MonitoringRow> rows;

  // Column flex weights, shared by the header and every data row so they align.
  static const int _borrowerFlex = 4;
  static const int _facilityFlex = 3;
  static const int _healthFlex = 2;
  static const int _statusFlex = 2;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;

    return Container(
      padding: const EdgeInsets.all(20),
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
            l.bankMonitoringTitle,
            style: textTheme.titleMedium?.copyWith(
              color: BaseerahTokens.darkText,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            l.bankMonitoringNote,
            style: textTheme.bodySmall?.copyWith(color: BaseerahTokens.muted),
          ),
          const SizedBox(height: 16),
          _HeaderRow(l: l),
          const Divider(height: 20),
          if (rows.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 24),
              child: Center(
                child: Text(
                  l.bankMonitoringEmpty,
                  style: textTheme.bodyMedium?.copyWith(
                    color: BaseerahTokens.muted,
                  ),
                ),
              ),
            )
          else
            for (var i = 0; i < rows.length; i++) ...[
              if (i > 0)
                Divider(
                  height: 1,
                  color: BaseerahTokens.darkText.withValues(alpha: 0.05),
                ),
              _DataRow(row: rows[i]),
            ],
        ],
      ),
    );
  }
}

/// The column-header row — muted, uppercase-weight labels aligned to the data
/// columns via the same flex weights.
class _HeaderRow extends StatelessWidget {
  const _HeaderRow({required this.l});

  final AppLocalizations l;

  @override
  Widget build(BuildContext context) {
    final style = Theme.of(context).textTheme.labelSmall?.copyWith(
      color: BaseerahTokens.muted,
      fontWeight: FontWeight.w700,
      letterSpacing: 0.4,
    );
    return Row(
      children: [
        Expanded(
          flex: MonitoringTable._borrowerFlex,
          child: Text(l.bankColBorrower, style: style),
        ),
        Expanded(
          flex: MonitoringTable._facilityFlex,
          child: Text(l.bankColFacility, style: style),
        ),
        Expanded(
          flex: MonitoringTable._healthFlex,
          child: Text(l.bankColHealth, style: style),
        ),
        Expanded(
          flex: MonitoringTable._statusFlex,
          child: Text(
            l.bankColStatus,
            style: style,
            textAlign: TextAlign.end,
          ),
        ),
      ],
    );
  }
}

/// One monitoring row: borrower, facility, health + trend arrow, status badge.
class _DataRow extends StatelessWidget {
  const _DataRow({required this.row});

  final MonitoringRow row;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 14),
      child: Row(
        children: [
          Expanded(
            flex: MonitoringTable._borrowerFlex,
            child: Text(
              row.borrower,
              style: textTheme.bodyMedium?.copyWith(
                color: BaseerahTokens.darkText,
                fontWeight: FontWeight.w600,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          Expanded(
            flex: MonitoringTable._facilityFlex,
            child: Text(
              row.facility,
              style: textTheme.bodySmall?.copyWith(color: BaseerahTokens.muted),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          Expanded(
            flex: MonitoringTable._healthFlex,
            child: Row(
              children: [
                Text(
                  '${row.health}',
                  style: textTheme.bodyMedium?.copyWith(
                    color: _healthColor(row.health),
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(width: 6),
                _TrendArrow(trend: row.trend),
              ],
            ),
          ),
          Expanded(
            flex: MonitoringTable._statusFlex,
            child: Align(
              alignment: AlignmentDirectional.centerEnd,
              child: _StatusBadge(status: row.status, l: l),
            ),
          ),
        ],
      ),
    );
  }
}

/// The ↑ / → / ↓ trend arrow. Up = green (improving), down = red (deteriorating),
/// flat = muted. Mirrored horizontally under RTL so the glyph points the
/// direction-correct way for Arabic.
class _TrendArrow extends StatelessWidget {
  const _TrendArrow({required this.trend});

  final Trend trend;

  @override
  Widget build(BuildContext context) {
    final (icon, color) = switch (trend) {
      Trend.up => (Icons.trending_up, BaseerahTokens.successGreen),
      Trend.down => (Icons.trending_down, BaseerahTokens.alertRed),
      Trend.flat => (Icons.trending_flat, BaseerahTokens.muted),
    };
    final isRtl = Directionality.of(context) == TextDirection.rtl;
    final arrow = Icon(icon, size: 18, color: color);
    return isRtl
        ? Transform.flip(flipX: true, child: arrow)
        : arrow;
  }
}

/// The colour-coded status pill (Healthy / Watch / At-risk), reusing the shared
/// zone palette (green / orange / red) with a soft tinted background.
class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.status, required this.l});

  final Status status;
  final AppLocalizations l;

  @override
  Widget build(BuildContext context) {
    final (color, label) = switch (status) {
      Status.healthy => (BaseerahTokens.successGreen, l.bankStatusHealthy),
      Status.watch => (BaseerahTokens.warningOrange, l.bankStatusWatch),
      Status.atRisk => (BaseerahTokens.alertRed, l.bankStatusAtRisk),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 5),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(9),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: color,
          fontWeight: FontWeight.w700,
          fontSize: 11,
        ),
      ),
    );
  }
}

/// Health-score colour on the shared zone thresholds (≥70 green, ≥50 orange,
/// else red) — the same scale the report's stamina box uses.
Color _healthColor(int health) {
  if (health >= 70) return BaseerahTokens.successGreen;
  if (health >= 50) return BaseerahTokens.warningOrange;
  return BaseerahTokens.alertRed;
}
