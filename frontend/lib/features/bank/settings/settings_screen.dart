import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../data/risk_policy_model.dart';
import '../state/bank_providers.dart';

/// The Bank Portal Settings screen (DESIGN §7.7): reads the singleton risk
/// policy from `GET /bank/risk-policy` and exposes it as a *Connectivity &
/// compliance* card (SAMA sync status, NDMO residency + tokenization toggles)
/// and a *Risk policy thresholds* card (stamina-floor + auto-decline sliders).
/// Every edit persists the whole policy via `PUT` and adopts the round-tripped
/// result, so the UI always reflects what the server stored (Step 6.4 task 5).
///
/// Persistence timing follows the natural control idiom: **toggles persist on
/// change** (a single discrete action), while **sliders persist on release**
/// (`onChangeEnd`) so a drag fires one `PUT`, not one per pixel. A failed save
/// surfaces a retry snackbar and the control reverts to the last saved value —
/// no silent failure. RTL-correct: the rows follow the ambient [Directionality].
class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final state = ref.watch(riskPolicyProvider);

    final Widget body = switch (state.phase) {
      RiskPolicyPhase.loading => const Center(child: CircularProgressIndicator()),
      RiskPolicyPhase.error => _SettingsError(
        l: l,
        onRetry: () => ref.read(riskPolicyProvider.notifier).load(),
      ),
      RiskPolicyPhase.ready => _SettingsBody(policy: state.policy!, saving: state.saving),
    };

    return Padding(
      padding: const EdgeInsets.all(BaseerahTokens.screenPadding),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l.navBankSettings,
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
              color: BaseerahTokens.darkText,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 20),
          Expanded(child: body),
        ],
      ),
    );
  }
}

/// The two settings cards over a loaded policy. A [ConsumerStatefulWidget] so the
/// sliders can show a smooth local value while dragging, committing on release.
class _SettingsBody extends ConsumerStatefulWidget {
  const _SettingsBody({required this.policy, required this.saving});

  final RiskPolicy policy;
  final bool saving;

  @override
  ConsumerState<_SettingsBody> createState() => _SettingsBodyState();
}

class _SettingsBodyState extends ConsumerState<_SettingsBody> {
  // Live drag values — non-null only while a slider is being dragged, so the
  // displayed value is [_staminaDraft] ?? the saved policy value. Cleared on
  // release: the round-tripped policy (or, on failure, the prior one) takes over.
  int? _staminaDraft;
  int? _autoDeclineDraft;

  /// Persist [edited]; on failure show a retry snackbar. Always clears the drag
  /// draft afterwards so the control snaps to the authoritative saved value
  /// (the new one on success, the prior one on failure — never a stuck ghost).
  Future<void> _save(RiskPolicy edited, {bool clearDrafts = false}) async {
    final ok = await ref.read(riskPolicyProvider.notifier).save(edited);
    if (!mounted) return;
    if (clearDrafts) {
      setState(() {
        _staminaDraft = null;
        _autoDeclineDraft = null;
      });
    }
    if (!ok) {
      final l = AppLocalizations.of(context);
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(l.bankSettingsSaveError)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final policy = widget.policy;
    final saving = widget.saving;
    final stamina = _staminaDraft ?? policy.staminaFloor;
    final autoDecline = _autoDeclineDraft ?? policy.autoDeclineThreshold;

    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _SettingsCard(
            title: l.bankSettingsConnectivity,
            children: [
              _SamaStatusRow(lastSync: policy.samaLastSync),
              const _RowDivider(),
              _ToggleRow(
                title: l.bankNdmoResidency,
                subtitle: l.bankNdmoSub,
                value: policy.ndmoResidency,
                // Disable while a save is in flight so a second toggle can't race.
                onChanged: saving
                    ? null
                    : (v) => _save(policy.copyWith(ndmoResidency: v)),
              ),
              const _RowDivider(),
              _ToggleRow(
                title: l.bankTokenization,
                subtitle: l.bankTokenizationSub,
                value: policy.tokenization,
                onChanged: saving
                    ? null
                    : (v) => _save(policy.copyWith(tokenization: v)),
              ),
            ],
          ),
          const SizedBox(height: 20),
          _SettingsCard(
            title: l.bankSettingsThresholds,
            children: [
              _SliderRow(
                title: l.bankStaminaFloor,
                note: l.bankStaminaFloorNote,
                valueLabel: '$stamina / 100',
                value: stamina.toDouble(),
                min: 0,
                max: 100,
                divisions: 100,
                onChanged: saving
                    ? null
                    : (v) => setState(() => _staminaDraft = v.round()),
                onChangeEnd: saving
                    ? null
                    : (v) => _save(
                        policy.copyWith(staminaFloor: v.round()),
                        clearDrafts: true,
                      ),
              ),
              const _RowDivider(),
              _SliderRow(
                title: l.bankAutoDecline,
                note: l.bankAutoDeclineNote,
                valueLabel: '$autoDecline%',
                value: autoDecline.toDouble(),
                min: 0,
                // Matches the server's validated bound (DTI ceiling = 200%).
                max: 200,
                divisions: 200,
                onChanged: saving
                    ? null
                    : (v) => setState(() => _autoDeclineDraft = v.round()),
                onChangeEnd: saving
                    ? null
                    : (v) => _save(
                        policy.copyWith(autoDeclineThreshold: v.round()),
                        clearDrafts: true,
                      ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// A titled settings card wrapping a set of rows (shared card tokens).
class _SettingsCard extends StatelessWidget {
  const _SettingsCard({required this.title, required this.children});

  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
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
            title,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              color: BaseerahTokens.darkText,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 8),
          ...children,
        ],
      ),
    );
  }
}

/// SAMA Open Banking status row: a green dot, the framework name, the last-sync
/// timestamp, and a "Connected" badge (DESIGN §7.7).
class _SamaStatusRow extends StatelessWidget {
  const _SamaStatusRow({required this.lastSync});

  final DateTime? lastSync;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final locale = Localizations.localeOf(context).toLanguageTag();
    final syncLabel = lastSync == null
        ? l.bankNeverSynced
        : '${l.bankLastSync} · ${DateFormat.yMMMd(locale).add_jm().format(lastSync!.toLocal())}';

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 14),
      child: Row(
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: BaseerahTokens.successGreen.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(10),
            ),
            alignment: Alignment.center,
            child: Container(
              width: 9,
              height: 9,
              decoration: const BoxDecoration(
                color: BaseerahTokens.successGreen,
                shape: BoxShape.circle,
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  l.bankSamaFramework,
                  style: textTheme.bodyMedium?.copyWith(
                    color: BaseerahTokens.darkText,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  syncLabel,
                  style: textTheme.bodySmall?.copyWith(
                    color: BaseerahTokens.muted,
                  ),
                ),
              ],
            ),
          ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 5),
            decoration: BoxDecoration(
              color: BaseerahTokens.successGreen.withValues(alpha: 0.10),
              borderRadius: BorderRadius.circular(9),
            ),
            child: Text(
              l.bankSamaConnected,
              style: const TextStyle(
                color: BaseerahTokens.successGreen,
                fontWeight: FontWeight.w700,
                fontSize: 11,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// A labelled toggle row (NDMO residency / tokenization). [onChanged] null while
/// a save is in flight disables the switch.
class _ToggleRow extends StatelessWidget {
  const _ToggleRow({
    required this.title,
    required this.subtitle,
    required this.value,
    required this.onChanged,
  });

  final String title;
  final String subtitle;
  final bool value;
  final ValueChanged<bool>? onChanged;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 14),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: textTheme.bodyMedium?.copyWith(
                    color: BaseerahTokens.darkText,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  subtitle,
                  style: textTheme.bodySmall?.copyWith(
                    color: BaseerahTokens.muted,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Switch(
            value: value,
            onChanged: onChanged,
            activeTrackColor: BaseerahTokens.teal,
          ),
        ],
      ),
    );
  }
}

/// A labelled threshold slider row with a live value chip (stamina floor /
/// auto-decline). Drag updates the local value; release commits it.
class _SliderRow extends StatelessWidget {
  const _SliderRow({
    required this.title,
    required this.note,
    required this.valueLabel,
    required this.value,
    required this.min,
    required this.max,
    required this.divisions,
    required this.onChanged,
    required this.onChangeEnd,
  });

  final String title;
  final String note;
  final String valueLabel;
  final double value;
  final double min;
  final double max;
  final int divisions;
  final ValueChanged<double>? onChanged;
  final ValueChanged<double>? onChangeEnd;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  title,
                  style: textTheme.bodyMedium?.copyWith(
                    color: BaseerahTokens.darkText,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: BaseerahTokens.teal.withValues(alpha: 0.10),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  valueLabel,
                  style: const TextStyle(
                    color: BaseerahTokens.teal,
                    fontWeight: FontWeight.w700,
                    fontSize: 12.5,
                  ),
                ),
              ),
            ],
          ),
          Slider(
            value: value.clamp(min, max),
            min: min,
            max: max,
            divisions: divisions,
            activeColor: BaseerahTokens.teal,
            onChanged: onChanged,
            onChangeEnd: onChangeEnd,
          ),
          Text(
            note,
            style: textTheme.bodySmall?.copyWith(color: BaseerahTokens.muted),
          ),
        ],
      ),
    );
  }
}

/// Hairline divider between settings rows (shared with the monitoring table's
/// row separators).
class _RowDivider extends StatelessWidget {
  const _RowDivider();

  @override
  Widget build(BuildContext context) {
    return Divider(
      height: 1,
      color: BaseerahTokens.darkText.withValues(alpha: 0.05),
    );
  }
}

/// Inline error + retry when the initial risk-policy load fails.
class _SettingsError extends StatelessWidget {
  const _SettingsError({required this.l, required this.onRetry});

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
            l.bankSettingsError,
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
