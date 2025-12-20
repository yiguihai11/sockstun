# HEV SOCKS5 Tunnel Unit Tests

This directory contains unit tests for the core routing components of the HEV SOCKS5 Tunnel.

## Test Components

### 1. Radix Tree Tests (`test-radix-tree`)
Tests the IPv4 CIDR matching implementation using a radix tree data structure.

**Coverage:**
- Basic tree creation and destruction
- IPv4 CIDR insertion and lookup
- Overlapping CIDR ranges (more specific rules win)
- CIDR notation parsing
- Edge cases (/0, /32, etc.)

### 2. Domain Hash Tests (`test-domain-hash`)
Tests the domain matching implementation using hash tables.

**Coverage:**
- Basic hash table operations
- Exact domain matching
- Wildcard domain matching (*.example.com)
- Suffix domain matching (.example.com)
- Case sensitivity options
- Hash table resizing
- Domain removal

### 3. Router Integration Tests (`test-router`)
Tests the complete router system combining IP and domain rules.

**Coverage:**
- IP rule matching with CIDR
- Domain rule matching (exact, wildcard, suffix)
- Port rule matching
- Combined IP and domain routing
- Rule priority (specific vs broad rules)
- Router statistics
- Edge cases and error handling

## Running Tests

### Using the test runner (recommended):
```bash
cd tests
./run-tests.sh
```

### Using Make directly:
```bash
cd tests
# Build all tests
make all

# Run all tests
make test

# Run individual tests
make test-radix    # Radix tree tests
make test-domain   # Domain hash tests
make test-router   # Router integration tests
```

### Running test executables directly:
```bash
cd tests
./test-radix-tree
./test-domain-hash
./test-router
```

## Test Framework

The tests use a simple test framework defined in `test-framework.h`:

- **TEST_ASSERT(condition, message)** - Basic assertion
- **TEST_ASSERT_EQ(expected, actual, message)** - Equality assertion
- **TEST_ASSERT_STR(expected, actual, message)** - String equality assertion
- **TEST_ASSERT_NULL(ptr, message)** - Null pointer assertion
- **TEST_ASSERT_NOT_NULL(ptr, message)** - Non-null pointer assertion

## Expected Output

Each test suite outputs:
- ✓ for passed tests
- ✗ for failed tests with file/line information
- Overall success rate percentage
- Final pass/fail status

Example output:
```
=== Running Radix Tree Tests ===
  ✓ Create new radix tree
  ✓ Empty tree returns NULL for lookup
  ✓ Insert /24 network
  ✓ Lookup exact IP
  ✓ Found correct value
  ...
✓ All tests passed!

=== Test Results ===
Total:  15
Passed: 15
Failed: 0
Success Rate: 100.0%

✓ All tests passed!
```

## Troubleshooting

### Compilation Issues
If tests fail to compile:
1. Ensure all source files are in the correct directories
2. Check that the parent directory structure matches the includes
3. Verify GCC is installed and supports C99

### Test Failures
If tests fail:
1. Check the specific assertion that failed
2. Review the test output for file and line information
3. Run individual tests to isolate issues

## Adding New Tests

To add a new test:

1. Create a new test file following the naming convention `test-*.c`
2. Include `test-framework.h`
3. Use the TEST_ASSERT macros for assertions
4. Update the Makefile to include the new test
5. Add a run_test call in `run-tests.sh`

Example new test structure:
```c
#include "test-framework.h"
#include "../src/module-to-test.h"

void test_new_feature(void)
{
    // Setup
    SomeStruct *obj = create_object();

    // Test assertions
    TEST_ASSERT_NOT_NULL(obj, "Object created successfully");
    TEST_ASSERT_EQ(expected_value, obj->property, "Property has correct value");

    // Cleanup
    destroy_object(obj);
}

int main(void)
{
    test_suite_start("New Feature Tests");
    test_new_feature();
    test_suite_end();
    print_test_results();
    return g_test_stats.failed > 0 ? 1 : 0;
}
```