import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/bank/applications/applications_screen.dart';
import '../features/bank/portfolio/portfolio_screen.dart';
import '../features/bank/settings/settings_screen.dart';
import '../features/goals/goals_screen.dart';
import '../features/home/home_screen.dart';
import '../features/rescue/rescue_screen.dart';
import '../features/simulate/simulate_screen.dart';
import '../l10n/app_localizations.dart';
import '../l10n/locale_provider.dart';
import '../shell/bottom_nav.dart';
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
          // Home is the first real screen (Step 2.3); the rest stay placeholders
          // until their phases land.
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: _consumerHome,
                builder: (context, state) => const HomeScreen(),
              ),
            ],
          ),
          // Simulate is a real screen from Step 3.5 (loan sliders + AI chat).
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: _consumerSimulate,
                builder: (context, state) => const SimulateScreen(),
              ),
            ],
          ),
          // Rescue is a real screen from Step 4.3 (shortfall banner, bridge
          // cards, before→after recovery).
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: _consumerRescue,
                builder: (context, state) => const RescueScreen(),
              ),
            ],
          ),
          // Goals is a real screen from Step 5.3 (gold points card, challenge
          // cards, live claim flow).
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: _consumerGoals,
                builder: (context, state) => const GoalsScreen(),
              ),
            ],
          ),
        ],
      ),
      // ── Bank portal shell: sidebar navigation ─────────────────────────────
      StatefulShellRoute.indexedStack(
        builder: (context, state, navShell) =>
            _BankShell(navigationShell: navShell),
        branches: [
          // Applications is the first real bank screen (Step 6.3): split-pane
          // applicant queue ↔ predictive report. Portfolio + Settings become
          // real screens in Step 6.4.
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: _bankApps,
                builder: (context, state) => const ApplicationsScreen(),
              ),
            ],
          ),
          // Portfolio (Step 6.4): 4 live KPI cards + monitoring table.
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: _bankPortfolio,
                builder: (context, state) => const PortfolioScreen(),
              ),
            ],
          ),
          // Settings (Step 6.4): SAMA status, NDMO/tokenization toggles, risk
          // thresholds — reads/persists the singleton risk policy.
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: _bankSettings,
                builder: (context, state) => const SettingsScreen(),
              ),
            ],
          ),
        ],
      ),
    ],
  );
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
    return Scaffold(
      appBar: const _ShellToolbar(isBank: false),
      body: navigationShell,
      bottomNavigationBar: ConsumerBottomNav(
        selectedIndex: navigationShell.currentIndex,
        onDestinationSelected: navigationShell.goBranch,
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
