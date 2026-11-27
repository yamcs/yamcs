# Decisions and assumptions

Format and rules: [CONTRIBUTING.md](../CONTRIBUTING.md#docs).

## Contents

- [Mission naming in tracked files](#mission-naming-in-tracked-files)

## Mission naming in tracked files

| ID | Assumption | Why | Affects | Status | Owner |
| --- | --- | --- | --- | --- | --- |
| DOC-1 | Tracked files refer to the mission, its MDB tooling, and its requirements repo with generic terms ("the mission", "mission profile", "mission defaults", "MDBs generated with the mission tooling", "a separate mission repo"). The real names live in the gitignored `CLAUDE.local.md`. | Keep mission-specific names out of the fork's tracked content. | `CONTRIBUTING.md`, `AGENTS.md`, `pus_analysis/*.md`, `docs/superpowers/` PUS wire-alignment spec and plan | Confirmed 2026-11-27 | nikhil-pixxel |
