#!/bin/bash

# Unit Test Runner Script
# Copyright (c) 2024 hev

set -e

echo "========================================"
echo "  HEV SOCKS5 Tunnel Unit Tests"
echo "========================================"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to run a test and check result
run_test() {
    local test_name=$1
    local test_exe=$2

    echo -e "${YELLOW}Running $test_name...${NC}"
    if ./$test_exe; then
        echo -e "${GREEN}✓ $test_name passed${NC}"
    else
        echo -e "${RED}✗ $test_name failed${NC}"
        return 1
    fi
    echo ""
}

# Check if tests are built
if [ ! -f "test-radix-tree" ] || [ ! -f "test-domain-hash" ] || [ ! -f "test-router" ]; then
    echo -e "${YELLOW}Tests not found, building...${NC}"
    make clean
    make all
fi

# Run all tests
FAILED=0

run_test "Radix Tree Tests" "test-radix-tree" || FAILED=1
run_test "Domain Hash Tests" "test-domain-hash" || FAILED=1
run_test "Router Integration Tests" "test-router" || FAILED=1

# Final summary
echo "========================================"
if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}All tests passed successfully!${NC}"
    exit 0
else
    echo -e "${RED}Some tests failed!${NC}"
    exit 1
fi