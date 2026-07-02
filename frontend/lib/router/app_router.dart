import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../l10n/app_localizations.dart';
import '../l10n/locale_provider.dart';
import '../theme/baseerah_theme.dart';

/// Consumer shell routes (bottom nav order: Home · Simulate · Rescue · Goals).
const _consumerHome = '/home';
const _consumerSimulate = '/simulate';
const _consumerRescue = '/rescue';
const _consumerGoals = '/goals';

/// Bank portal shell routes (sidebar order: Applications · Portfolio · Settings).
const _bankApps = '/bank/apps';
const _bankPortfolio = '/bank/portfolio';
const _bankSettings = '/bank/settings';

/// Builds the app's [GoRouter]. Two independent stateful shells — the consumer
/// mobile app and the bank desktop portal — reachable from each other via the
/// toolbar's Consumer/Bank switch. Real screens replace the placeholders in
/// Phases 2–6.
GoRouter createRouter() {
  return GoRouter(
    initialLocation: _consumerHome,
    routes: [
      // ── Consumer shell: bottom navigation ─────────────────────────────────
      StatefulShellRoute.indexedStack(
        builder: (context, state, navShell) =>
            _ConsumerShell(navigationShell: navShell),
        branches: [
          _branch(_consumerHome, (l) => l.navHome, Icons.home_outlined),
          _branch(
            _consumerSimulate,
            (l) => l.navSimulate,
            Icons.tune_outlined,
          ),
          _branch(
            _consumerRescue,
            (l) => l.navRescue,
            Icons.health_and_safety_outlined,
          ),
          _branch(_consumerGoals, (l) => l.navGoals, Icons.flag_outlined),
        ],
      ),
      // ── Bank portal shell: sidebar navigation ─────────────────────────────
      StatefulShellRoute.indexedStack(
        builder: (context, state, navShell) =>
            _BankShell(navigationShell: navShell),
        branches: [
          _branch(
            _bankApps,
            (l) => l.navBankApplications,
            Icons.assignment_outlined,
          ),
          _branch(
            _bankPortfolio,
            (l) => l.navBankPortfolio,
            Icons.pie_chart_outline,
          ),
          _branch(
            _bankSettings,
            (l) => l.navBankSettings,
            Icons.settings_outlined,
          ),
        ],
      ),
    ],
  );
}

/// Label resolver for a route — reads the localized nav label at build time.
typedef _LabelOf = String Function(AppLocalizations l);

/// A single-route branch rendering a [_PlaceholderScreen]. The [labelOf] and
/// [icon] are stashed on the route so the shells can build their nav items.
StatefulShellBranch _branch(String path, _LabelOf labelOf, IconData icon) {
  return StatefulShellBranch(
    routes: [
      GoRoute(
        path: path,
        builder: (context, state) =>
            _PlaceholderScreen(labelOf: labelOf, icon: icon),
      ),
    ],
  );
}

/// Minimal placeholder shown for every route until real screens land.
class _PlaceholderScreen extends StatelessWidget {
  const _PlaceholderScreen({required this.labelOf, required this.icon});

  final _LabelOf labelOf;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: 48, color: BaseerahTokens.teal),
          const SizedBox(height: 12),
          Text(labelOf(l), style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 8),
          Text(
            l.comingSoon,
            style: Theme.of(
              context,
            ).textTheme.bodyMedium?.copyWith(color: BaseerahTokens.muted),
          ),
        ],
      ),
    );
  }
}

/// Shared toolbar: logo, Consumer/Bank switch, language toggle. Present on both
/// shells so the two are always reachable and the language can flip anywhere.
class _ShellToolbar extends ConsumerWidget implements PreferredSizeWidget {
  const _ShellToolbar({required this.isBank});

  final bool isBank;

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    return AppBar(
      title: Text(l.appTitle),
      actions: [
        SegmentedButton<bool>(
          style: SegmentedButton.styleFrom(
            foregroundColor: Colors.white,
            selectedForegroundColor: BaseerahTokens.teal,
            selectedBackgroundColor: Colors.white,
            side: const BorderSide(color: Colors.white54),
          ),
          segments: [
            ButtonSegment(value: false, label: Text(l.shellConsumer)),
            ButtonSegment(value: true, label: Text(l.shellBank)),
          ],
          selected: {isBank},
          showSelectedIcon: false,
          onSelectionChanged: (sel) {
            final goBank = sel.first;
            if (goBank == isBank) return;
            context.go(goBank ? _bankApps : _consumerHome);
          },
        ),
        const SizedBox(width: 8),
        TextButton(
          onPressed: () => ref.read(localeProvider.notifier).toggle(),
          style: TextButton.styleFrom(foregroundColor: Colors.white),
          child: Text(l.languageToggle),
        ),
        const SizedBox(width: 8),
      ],
    );
  }
}

/// Consumer mobile shell — bottom navigation bar.
class _ConsumerShell extends StatelessWidget {
  const _ConsumerShell({required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Scaffold(
      appBar: const _ShellToolbar(isBank: false),
      body: navigationShell,
      bottomNavigationBar: NavigationBar(
        selectedIndex: navigationShell.currentIndex,
        onDestinationSelected: navigationShell.goBranch,
        destinations: [
          NavigationDestination(
            icon: const Icon(Icons.home_outlined),
            label: l.navHome,
          ),
          NavigationDestination(
            icon: const Icon(Icons.tune_outlined),
            label: l.navSimulate,
          ),
          NavigationDestination(
            icon: const Icon(Icons.health_and_safety_outlined),
            label: l.navRescue,
          ),
          NavigationDestination(
            icon: const Icon(Icons.flag_outlined),
            label: l.navGoals,
          ),
        ],
      ),
    );
  }
}

/// Bank portal shell — left sidebar (NavigationRail) for a desktop layout.
class _BankShell extends StatelessWidget {
  const _BankShell({required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Scaffold(
      appBar: const _ShellToolbar(isBank: true),
      body: Row(
        children: [
          NavigationRail(
            selectedIndex: navigationShell.currentIndex,
            onDestinationSelected: navigationShell.goBranch,
            labelType: NavigationRailLabelType.all,
            destinations: [
              NavigationRailDestination(
                icon: const Icon(Icons.assignment_outlined),
                label: Text(l.navBankApplications),
              ),
              NavigationRailDestination(
                icon: const Icon(Icons.pie_chart_outline),
                label: Text(l.navBankPortfolio),
              ),
              NavigationRailDestination(
                icon: const Icon(Icons.settings_outlined),
                label: Text(l.navBankSettings),
              ),
            ],
          ),
          const VerticalDivider(width: 1),
          Expanded(child: navigationShell),
        ],
      ),
    );
  }
}
