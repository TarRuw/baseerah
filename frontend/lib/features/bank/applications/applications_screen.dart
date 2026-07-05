import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import 'applicant_list.dart';
import 'report_detail.dart';

/// The Bank Portal Applications screen (DESIGN §7.5): a desktop split pane over
/// the bank shell's sidebar — a fixed-width applicant queue on the (start) side
/// and the selection-driven predictive report detail filling the rest. Both
/// panes are RTL-aware: `Row` follows the ambient [Directionality], so under
/// Arabic the list sits on the right and the detail on the left automatically.
class ApplicationsScreen extends StatelessWidget {
  const ApplicationsScreen({super.key});

  /// Fixed queue width (matches the prototype's 340 px list column).
  static const double _listWidth = 340;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Padding(
      padding: const EdgeInsets.all(BaseerahTokens.screenPadding),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l.bankApplicationsTitle,
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
              color: BaseerahTokens.darkText,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 16),
          const Expanded(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SizedBox(width: _listWidth, child: ApplicantList()),
                SizedBox(width: 20),
                Expanded(child: ReportDetail()),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
