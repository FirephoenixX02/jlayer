# JLayer Codebase Guide for Agents

## Build, Lint, and Test Commands

### Build Commands
- `mvn clean package` - Build the entire project
- `mvn compile` - Compile source code
- `mvn test` - Run all tests
- `mvn clean test` - Clean and run tests

### Running Single Tests
- `mvn test -Dtest=BitstreamTest` - Run a specific test class
- `mvn test -Dtest=jlpTest` - Run a specific test class

### Code Style Guidelines

#### Java Language
- Java 17
- Use 4 spaces for indentation (no tabs)
- Follow Java naming conventions:
  - Class names: PascalCase
  - Method names: camelCase
  - Constants: UPPER_CASE
  - Package names: lowercase

#### Imports
- Organize imports in order:
  1. Java standard library imports
  2. Third-party library imports
  3. Project-specific imports
- Use static imports for commonly used constants
- Sort imports alphabetically within categories

#### Formatting
- Line length: 120 characters maximum
- No trailing whitespace
- One statement per line
- Braces on same line for methods and control structures
- Proper spacing around operators and after commas

#### Error Handling
- Use try-catch blocks for checked exceptions
- Prefer specific exception types over generic ones
- Log exceptions appropriately when possible
- Avoid empty catch blocks

#### Naming Conventions
- Use descriptive names for classes, methods, and variables
- Avoid abbreviations unless widely known (e.g., `URL`, `HTTP`)
- Use `is` prefix for boolean variables
- Method names should be verbs, class names should be nouns

#### Documentation
- Use JavaDoc comments for public classes and methods
- Document parameters and return values
- Include examples where helpful

#### Testing
- All tests must be in the `src/test/java/` directory
- Test class names should end with `Test`
- Use JUnit 5 for testing framework
- Tests should be independent and idempotent

## Cursor Rules
No specific Cursor rules found in .cursor/rules/

## Copilot Instructions
No specific Copilot instructions found in .github/copilot-instructions.md

## Notes
- This codebase is a Java project built with Maven
- Uses JUnit 5 for testing
- Compatible with Java 17
- Project structure follows Maven conventions