import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/auth/data/auth_models.dart';
import '../features/auth/login_mobile_screen.dart';
import '../features/auth/state/auth_providers.dart';
import '../features/bank/applications/applications_screen.dart';
import '../features/bank/disbursements/disbursements_screen.dart';
import '../features/bank/financing/financing_requests_screen.dart';
import '../features/bank/portfolio/portfolio_screen.dart';
import '../features/bank/settings/settings_screen.dart';
import '../features/expenses/expenses_screen.dart';
import '../features/financing/financing_screen.dart';
import '../features/goals/goals_screen.dart';
import '../features/home/home_screen.dart';
import '../features/rescue/rescue_screen.dart';
import '../features/simulate/simulate_screen.dart';
import '../l10n/app_localizations.dart';
import '../l10n/locale_provider.dart';
import '../shell/bottom_nav.dart';
import '../shell/responsive_frame.dart';
import '../theme/baseerah_theme.dart';

/// Login (Step 9.5): the only route reachable while signed out.
const _login = '/login';

/// Consumer shell routes (bottom nav order: Home · Simulate · Rescue · Goals ·
/// Expenses).
const _consumerHome = '/home';
const _consumerSimulate = '/simulate';
const _consumerRescue = '/rescue';
const _consumerGoals = '/goals';
const _consumerExpenses = '/expenses';

/// Bank portal shell routes — one loan pipeline in stages (sidebar order:
/// Underwrite · Price · Disburse · Portfolio · Settings). Underwrite is the
/// request-model successor to the retired FR-08 Applications tab (Step 12.6).
const _bankUnderwrite = '/bank/underwrite';
const _bankPrice = '/bank/price';
const _bankDisburse = '/bank/disburse';
const _bankPortfolio = '/bank/portfolio';
const _bankSettings = '/bank/settings';

/// The app's [GoRouter], built once and rebuilt never (it re-reads auth state on
/// each redirect via [ref] and re-evaluates redirects through a
/// [refreshListenable] bound to [authControllerProvider]).
///
/// Gating (Step 9.5): signed-out users can only reach `/login`; signed-in users
/// land on — and are held within — the shell for their role. A consumer can't
/// reach `/bank/**` and a bank officer can't reach the consumer screens; the
/// router redirect is the UX half of that, the backend `403`s (Step 9.3) the
/// enforced half.
final routerProvider = Provider<GoRouter>((ref) {
  // A Listenable the router refreshes on: fires whenever auth state changes so a
  // login/logout re-runs the redirect immediately.
  final refresh = ValueNotifier<AuthState>(ref.read(authControllerProvider));
  ref.onDispose(refresh.dispose);
  ref.listen<AuthState>(
    authControllerProvider,
    (_, next) => refresh.value = next,
  );

  String roleLanding(AuthUser user) =>
      user.role == AuthRole.bank ? _bankUnderwrite : _consumerHome;

  final initial = ref.read(authControllerProvider);
  final initialLocation =
      initial.isAuthenticated ? roleLanding(initial.user!) : _login;

  return GoRouter(
    initialLocation: initialLocation,
    refreshListenable: refresh,
    redirect: (context, state) {
      final auth = ref.read(authControllerProvider);
      final atLogin = state.matchedLocation == _login;

      // Signed out (or an OTP verify still in flight): only /login is allowed.
      if (!auth.isAuthenticated) {
        return atLogin ? null : _login;
      }

      // Signed in: keep the user inside their role's shell.
      final atBankRoute = state.matchedLocation.startsWith('/bank');
      if (auth.user!.role == AuthRole.bank) {
        return (atLogin || !atBankRoute) ? _bankUnderwrite : null;
      }
      return (atLogin || atBankRoute) ? _consumerHome : null;
    },
    routes: [
      // ── Login (outside both shells) ───────────────────────────────────────
      GoRoute(
        path: _login,
        builder: (context, state) => const LoginMobileScreen(),
      ),

      // ── Consumer shell: bottom navigation ─────────────────────────────────
      StatefulShellRoute.indexedStack(
        builder: (context, state, navShell) =>
            _ConsumerShell(navigationShell: navShell),
        branches: [
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: _consumerHome,
                builder: (context, state) => const HomeScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: _consumerSimulate,
                builder: (context, state) => const SimulateScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: _consumerRescue,
                builder: (context, state) => const RescueScreen(),
                routes: [
                  GoRoute(
                    path: 'financing',
                    builder: (context, state) =>
                        FinancingScreen(args: state.extra as FinancingArgs),
                  ),
                ],
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: _consumerGoals,
                builder: (context, state) => const GoalsScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: _consumerExpenses,
                builder: (context, state) => const ExpensesScreen(),
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
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: _bankUnderwrite,
                builder: (context, state) => const ApplicationsScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: _bankPrice,
                builder: (context, state) => const FinancingRequestsScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: _bankDisburse,
                builder: (context, state) => const DisbursementsScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: _bankPortfolio,
                builder: (context, state) => const PortfolioScreen(),
              ),
            ],
          ),
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
});

/// Shared toolbar: logo/title, the signed-in user's name + logout, and the
/// language toggle. The role now decides the shell, so the old Consumer/Bank
/// switch is gone (Step 9.5).
class _ShellToolbar extends ConsumerWidget implements PreferredSizeWidget {
  const _ShellToolbar();

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final isArabic = Localizations.localeOf(context).languageCode == 'ar';
    final user = ref.watch(currentUserProvider);
    final name =
        user == null ? '' : (isArabic ? user.displayNameAr : user.displayName);

    return AppBar(
      title: Text(l.appTitle),
      actions: [
        if (user != null)
          PopupMenuButton<_AccountAction>(
            tooltip: name,
            onSelected: (action) {
              if (action == _AccountAction.logout) {
                // Router redirect returns to /login when the state flips.
                ref.read(authControllerProvider.notifier).logout();
              }
            },
            itemBuilder: (context) => [
              PopupMenuItem<_AccountAction>(
                value: _AccountAction.logout,
                child: Row(
                  children: [
                    const Icon(Icons.logout, size: 18),
                    const SizedBox(width: 12),
                    Text(l.logout),
                  ],
                ),
              ),
            ],
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.account_circle_outlined,
                      color: Colors.white),
                  const SizedBox(width: 6),
                  ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 120),
                    child: Text(
                      name,
                      overflow: TextOverflow.ellipsis,
                      softWrap: false,
                      style: const TextStyle(color: Colors.white),
                    ),
                  ),
                  const Icon(Icons.arrow_drop_down, color: Colors.white),
                ],
              ),
            ),
          ),
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

/// Account-menu actions in the shell toolbar.
enum _AccountAction { logout }

/// Consumer mobile shell — bottom navigation bar.
class _ConsumerShell extends StatelessWidget {
  const _ConsumerShell({required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  @override
  Widget build(BuildContext context) {
    // Cap to a phone frame on wide web/desktop (QA UI-01); a no-op on real phones.
    return ResponsiveFrame(
      maxWidth: BaseerahTokens.phoneFrameMaxWidth,
      child: Scaffold(
        appBar: const _ShellToolbar(),
        body: navigationShell,
        bottomNavigationBar: ConsumerBottomNav(
          selectedIndex: navigationShell.currentIndex,
          onDestinationSelected: navigationShell.goBranch,
        ),
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
    // A desktop layout by design — fills the full window width (no cap), no phone
    // frame (framed: false).
    return ResponsiveFrame(
      maxWidth: double.infinity,
      framed: false,
      child: Scaffold(
        appBar: const _ShellToolbar(),
        body: Row(
          children: [
            NavigationRail(
              selectedIndex: navigationShell.currentIndex,
              onDestinationSelected: navigationShell.goBranch,
              labelType: NavigationRailLabelType.all,
              destinations: [
                NavigationRailDestination(
                  icon: const Icon(Icons.fact_check_outlined),
                  label: Text(l.navBankApplications),
                ),
                NavigationRailDestination(
                  icon: const Icon(Icons.request_quote_outlined),
                  label: Text(l.navBankFinancing),
                ),
                NavigationRailDestination(
                  icon: const Icon(Icons.account_balance_wallet_outlined),
                  label: Text(l.navBankDisbursements),
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
      ),
    );
  }
}
