import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';

/// Reserved slot for the 30-day cash-flow forecast chart (DESIGN §7.1).
///
/// The real data source is the Phase 3 Step 3.2 forecast endpoint; this renders
/// a titled empty/skeleton state so Home never blocks or overflows waiting for
/// it. Structured so wiring the chart later is a drop-in (swap the body).
class ForecastChartPlaceholder extends StatelessWidget {
  const ForecastChartPlaceholder({super.key});

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
        boxShadow: BaseerahTokens.shadowSoft,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l.forecast30Title,
            style: textTheme.titleMedium?.copyWith(
              color: BaseerahTokens.darkText,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 12),
          // Skeleton chart area: a neutral, non-interactive placeholder box.
          Container(
            height: 120,
            decoration: BoxDecoration(
              color: BaseerahTokens.lightBg,
              borderRadius: BorderRadius.circular(BaseerahTokens.radiusControl),
            ),
            alignment: Alignment.center,
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  Icons.show_chart,
                  size: 18,
                  color: BaseerahTokens.muted,
                ),
                const SizedBox(width: 8),
                Text(
                  l.forecastComingSoon,
                  style: textTheme.bodySmall?.copyWith(
                    color: BaseerahTokens.muted,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
