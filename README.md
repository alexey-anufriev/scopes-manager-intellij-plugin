# Scopes Manager IntelliJ Plugin

[![JetBrains IntelliJ Plugins](https://img.shields.io/jetbrains/plugin/v/14987-scopes-manager)](https://plugins.jetbrains.com/plugin/14987-scopes-manager)
[![JetBrains IntelliJ plugins](https://img.shields.io/jetbrains/plugin/d/14987-scopes-manager)](https://plugins.jetbrains.com/plugin/14987-scopes-manager)
[![JetBrains IntelliJ Plugins](https://img.shields.io/jetbrains/plugin/r/rating/14987-scopes-manager)](https://plugins.jetbrains.com/plugin/14987-scopes-manager)

Scopes bring more structure and easier navigation across the project tree. 
Scopes Manager Plugin is important for handy scopes management of the project resources.
Any file or folder can be assigned to a scope (or unassigned) right from the tree view.

Plugin extends context menu of the project tree with a couple of new items:

![context menu](./docs/menu-items.png)

### Scopes Settings

There are two settings navigation shortcuts in this group:
* Scopes List
* Scopes Colors

#### Scopes List

Navigates to Scopes Settings where scopes can be defined:

![scopes list](./docs/scopes-configuration.png)

#### Scopes Colors

Navigates to Colors Settings where colors can be assigned to scopes:

![scopes list](./docs/colors-configuration.png)

### Add to Scope

Shortcut: `Alt + S` / `⌥+S` (can be reassigned in IDEA settings)

The Scope can be either selected from existing ones or created new.
Additionally, if Tasks Management plugin is enabled and used (any non-default task exist),
then new Scopes can be created for available tasks.

### Remove from Scope

Shortcut: `Alt + D` / `⌥+D` (can be reassigned in IDEA settings)

Allows to remove selected resources from the scope.

### Clear Scope

Allows to clear the scope content completely.

### Switch Scope

Shortcut: `Alt + Shift + S` / `⌥+⇧+S` (can be reassigned in IDEA settings)

Opens a popup with all user-defined scopes and switches the Project view to the selected one without leaving the keyboard.
The first entry, `Project`, disables the Scope view and returns to the regular Project view.
Also discoverable via `Find Action` (`Ctrl+Shift+A` / `⇧⌘A`) as `Switch Scope`.

![switch scopes](./docs/switch-scopes.png)

## Sample View

![legacy scope](docs/assigned-scopes.png)

## ☕ Support

Enjoying this plugin?

<a href="https://www.buymeacoffee.com/alexey.anufriev" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" style="height: 60px !important;width: 217px !important;" ></a>

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md) for contribution guidelines
and DCO signoff requirements.
