import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/app_localizations.dart';
import '../../theme/baseerah_theme.dart';
import 'widgets/ask_ai_tab.dart';
import 'widgets/loan_affordability_tab.dart';

/// Consumer Simulate screen (DESIGN §7.2): a title/subtitle header, a two-pill
/// tab control, and one of the two tabs — Loan Affordability (live sliders) or
/// Ask Baseerah AI (chat). Both tabs are driven entirely by the Phase-3 backend.
///
/// Local `int` tab state is fine here: which tab is showing is ephemeral UI, not
/// app data, so it doesn't belong in a provider. The tabs themselves own their
/// data via Riverpod.
class SimulateScreen extends ConsumerStatefulWidget {
  const SimulateScreen({super.key});

  @override
  ConsumerState<SimulateScreen> createState() => _SimulateScreenState();
}

class _SimulateScreenState extends ConsumerState<SimulateScreen> {
  int _tab = 0;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;

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
              l.simulateTitle,
              style: textTheme.headlineSmall?.copyWith(
                color: BaseerahTokens.darkText,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              l.simulateSubtitle,
              style: textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted),
            ),
            const SizedBox(height: 16),
            _TabSwitcher(
              index: _tab,
              labels: [l.loanTab, l.aiTab],
              onChanged: (i) => setState(() => _tab = i),
            ),
            const SizedBox(height: 18),
            Expanded(
              child: _tab == 0 ? const LoanAffordabilityTab() : const AskAiTab(),
            ),
          ],
        ),
      ),
    );
  }
}

/// Two-pill segmented control (prototype §7.2): a rounded track with the active
/// pill in teal. Kept custom (not Material's SegmentedButton) to match the
/// prototype's look; it lays out in logical order, so it mirrors under RTL.
class _TabSwitcher extends StatelessWidget {
  const _TabSwitcher({
    required this.index,
    required this.labels,
    required this.onChanged,
  });

  final int index;
  final List<String> labels;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: const Color(0xFFEAE7DE),
        borderRadius: BorderRadius.circular(13),
      ),
      child: Row(
        children: [
          for (var i = 0; i < labels.length; i++)
            Expanded(child: _pill(context, i)),
        ],
      ),
    );
  }

  Widget _pill(BuildContext context, int i) {
    final selected = i == index;
    return GestureDetector(
      onTap: () => onChanged(i),
      behavior: HitTestBehavior.opaque,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(vertical: 10),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: selected ? BaseerahTokens.teal : Colors.transparent,
          borderRadius: BorderRadius.circular(10),
        ),
        child: Text(
          labels[i],
          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
            color: selected ? Colors.white : BaseerahTokens.muted,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
    );
  }
}
