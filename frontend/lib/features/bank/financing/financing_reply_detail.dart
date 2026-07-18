import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format.dart';
import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../applications/applicant_list.dart' show RiskBadge;
import '../data/financing_request_models.dart';
import '../state/bank_providers.dart';

/// Right pane of the Financing Requests split view: the reply form for the
/// selected pending proposal. Shows the request context (bank, applicant,
/// requested amount, client-score risk hint), two inputs (profit rate %, term
/// months), and Send-offer / Decline actions. Once answered it shows a
/// confirmation. Empty until a row is picked.
class FinancingReplyDetail extends ConsumerStatefulWidget {
  const FinancingReplyDetail({super.key});

  @override
  ConsumerState<FinancingReplyDetail> createState() =>
      _FinancingReplyDetailState();
}

class _FinancingReplyDetailState extends ConsumerState<FinancingReplyDetail> {
  final _rateController = TextEditingController();
  final _termController = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  String? _lastProposalId;

  @override
  void dispose() {
    _rateController.dispose();
    _termController.dispose();
    super.dispose();
  }

  /// Reset the inputs when the selected proposal changes.
  void _syncTo(FinancingRequestRow? row) {
    if (row?.proposalId != _lastProposalId) {
      _lastProposalId = row?.proposalId;
      _rateController.text = '';
      _termController.text = '';
    }
  }

  Future<void> _send() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    final rate = double.parse(_rateController.text.trim());
    final term = int.parse(_termController.text.trim());
    final ok = await ref.read(bankFinancingProvider.notifier).reply(rate, term);
    if (!ok && mounted) _snack(AppLocalizations.of(context).bankFinancingReplyError);
  }

  Future<void> _decline() async {
    final ok = await ref.read(bankFinancingProvider.notifier).decline();
    if (!ok && mounted) _snack(AppLocalizations.of(context).bankFinancingReplyError);
  }

  void _snack(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final state = ref.watch(bankFinancingProvider);
    final row = state.selected;
    _syncTo(row);

    if (row == null) {
      return _CenteredHint(icon: Icons.description_outlined, text: l.bankFinancingSelectPrompt);
    }
    if (state.answered) {
      return _AnsweredView(row: row, l: l);
    }
    return _ReplyForm(
      row: row,
      formKey: _formKey,
      rateController: _rateController,
      termController: _termController,
      submitting: state.submitting,
      onSend: _send,
      onDecline: _decline,
    );
  }
}

class _ReplyForm extends StatelessWidget {
  const _ReplyForm({
    required this.row,
    required this.formKey,
    required this.rateController,
    required this.termController,
    required this.submitting,
    required this.onSend,
    required this.onDecline,
  });

  final FinancingRequestRow row;
  final GlobalKey<FormState> formKey;
  final TextEditingController rateController;
  final TextEditingController termController;
  final bool submitting;
  final VoidCallback onSend;
  final VoidCallback onDecline;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final fmt = Fmt(Localizations.localeOf(context), l);
    final textTheme = Theme.of(context).textTheme;

    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            row.bankName,
            style: textTheme.titleLarge?.copyWith(
              color: BaseerahTokens.darkText,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            row.applicantLabel,
            style: textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted),
          ),
          const SizedBox(height: 20),
          // Requested amount + risk hints. The verdict + stamina are the parent
          // request's underwriting result carried down as pricing decision
          // support (Step 12.6); both are shown only once the request has been
          // underwritten (else the operator is pricing before a report exists).
          Wrap(
            spacing: 28,
            runSpacing: 12,
            children: [
              _Fact(label: l.bankFinancingRequestedAmount, value: fmt.money(row.amount)),
              _Fact(label: l.bankFinancingClientScore, value: '${row.clientScore}'),
              if (row.verdict != null)
                _VerdictFact(badge: RiskBadge.of(row.verdict, l), l: l),
              if (row.staminaScore != null)
                _Fact(label: l.bankStaminaScore, value: '${row.staminaScore}'),
            ],
          ),
          const SizedBox(height: 24),
          Form(
            key: formKey,
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: _NumberField(
                    controller: rateController,
                    label: l.bankFinancingRate,
                    suffix: '%',
                    allowDecimal: true,
                    max: 100,
                    invalid: l.bankFinancingInvalid,
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: _NumberField(
                    controller: termController,
                    label: l.bankFinancingTerm,
                    allowDecimal: false,
                    max: 600,
                    invalid: l.bankFinancingInvalid,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          Row(
            children: [
              ElevatedButton(
                onPressed: submitting ? null : onSend,
                style: ElevatedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 14),
                ),
                child: submitting
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                      )
                    : Text(l.bankFinancingReply),
              ),
              const SizedBox(width: 12),
              OutlinedButton(
                onPressed: submitting ? null : onDecline,
                child: Text(l.bankFinancingDecline),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// A labelled numeric field with light validation (required, numeric, ≤ max).
class _NumberField extends StatelessWidget {
  const _NumberField({
    required this.controller,
    required this.label,
    required this.allowDecimal,
    required this.max,
    required this.invalid,
    this.suffix,
  });

  final TextEditingController controller;
  final String label;
  final bool allowDecimal;
  final double max;
  final String invalid;
  final String? suffix;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: controller,
      keyboardType: TextInputType.numberWithOptions(decimal: allowDecimal),
      inputFormatters: [
        FilteringTextInputFormatter.allow(
          allowDecimal ? RegExp(r'[0-9.]') : RegExp(r'[0-9]'),
        ),
      ],
      decoration: InputDecoration(
        labelText: label,
        suffixText: suffix,
        border: const OutlineInputBorder(),
        isDense: true,
      ),
      validator: (value) {
        final text = (value ?? '').trim();
        if (text.isEmpty) return invalid;
        final n = num.tryParse(text);
        if (n == null || n < 0 || n > max) return invalid;
        if (!allowDecimal && n != n.roundToDouble()) return invalid;
        return null;
      },
    );
  }
}

/// A fact whose value is the underwriting risk badge (verdict) rather than plain
/// text — sits alongside the plain [_Fact]s as pricing decision support.
class _VerdictFact extends StatelessWidget {
  const _VerdictFact({required this.badge, required this.l});

  final RiskBadge badge;
  final AppLocalizations l;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          l.bankUnderwritingVerdict,
          style: textTheme.bodySmall?.copyWith(color: BaseerahTokens.muted),
        ),
        const SizedBox(height: 4),
        badge.build(context),
      ],
    );
  }
}

class _Fact extends StatelessWidget {
  const _Fact({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: textTheme.bodySmall?.copyWith(color: BaseerahTokens.muted)),
        const SizedBox(height: 2),
        Text(
          value,
          style: textTheme.titleMedium?.copyWith(
            color: BaseerahTokens.darkText,
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    );
  }
}

/// Post-answer confirmation (offer sent or declined).
class _AnsweredView extends StatelessWidget {
  const _AnsweredView({required this.row, required this.l});

  final FinancingRequestRow row;
  final AppLocalizations l;

  @override
  Widget build(BuildContext context) {
    final declined = row.status == ProposalReviewStatus.declined;
    final color = declined ? BaseerahTokens.muted : BaseerahTokens.successGreen;
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            declined ? Icons.block : Icons.check_circle_rounded,
            color: color,
            size: 48,
          ),
          const SizedBox(height: 12),
          Text(
            declined ? l.bankFinancingDeclined : l.bankFinancingReplied,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              color: BaseerahTokens.darkText,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}

class _CenteredHint extends StatelessWidget {
  const _CenteredHint({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 44, color: BaseerahTokens.muted),
          const SizedBox(height: 12),
          Text(
            text,
            textAlign: TextAlign.center,
            style: Theme.of(context)
                .textTheme
                .bodyMedium
                ?.copyWith(color: BaseerahTokens.muted),
          ),
        ],
      ),
    );
  }
}
