import 'package:flutter/material.dart';

import '../l10n/app_localizations.dart';

/// Consumer shell bottom navigation: Home · Simulate · Rescue · Goals
/// (DESIGN §7). Stateless — the owning [StatefulNavigationShell] holds the
/// selected index and branch switching; this only renders the bar. Labels are
/// localized and mirror correctly under RTL because order follows reading order.
class ConsumerBottomNav extends StatelessWidget {
  const ConsumerBottomNav({
    super.key,
    required this.selectedIndex,
    required this.onDestinationSelected,
  });

  final int selectedIndex;
  final ValueChanged<int> onDestinationSelected;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return NavigationBar(
      selectedIndex: selectedIndex,
      onDestinationSelected: onDestinationSelected,
      destinations: [
        NavigationDestination(
          icon: const Icon(Icons.home_outlined),
          selectedIcon: const Icon(Icons.home),
          label: l.navHome,
        ),
        NavigationDestination(
          icon: const Icon(Icons.tune_outlined),
          selectedIcon: const Icon(Icons.tune),
          label: l.navSimulate,
        ),
        NavigationDestination(
          icon: const Icon(Icons.health_and_safety_outlined),
          selectedIcon: const Icon(Icons.health_and_safety),
          label: l.navRescue,
        ),
        NavigationDestination(
          icon: const Icon(Icons.flag_outlined),
          selectedIcon: const Icon(Icons.flag),
          label: l.navGoals,
        ),
      ],
    );
  }
}
