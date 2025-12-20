# Makefile for unit tests

CC = gcc
CFLAGS = -Wall -Wextra -g -std=c99 -I../src -I../src/route -I../src/route/router -I../src/misc -I../third-part/lwip/src/include

# Source directories
SRC_DIR = ../src
ROUTE_DIR = $(SRC_DIR)/route
ROUTE_ROUTER_DIR = $(ROUTE_DIR)/router

# Test executables
RADIX_TEST = test-radix-tree
DOMAIN_TEST = test-domain-hash
IPV6_TEST = test-ipv6
ROUTER_TEST = test-router

# Source files
TEST_LOGGER = test-logger.c
RADIX_SRC = test-radix-tree.c test-framework.c $(TEST_LOGGER) $(ROUTE_ROUTER_DIR)/hev-radix-tree.c
DOMAIN_SRC = test-domain-hash.c test-framework.c $(TEST_LOGGER) $(ROUTE_ROUTER_DIR)/hev-domain-hash.c
IPV6_SRC = test-ipv6.c test-framework.c $(TEST_LOGGER) $(ROUTE_ROUTER_DIR)/hev-radix-tree.c
ROUTER_SRC = test-router.c test-framework.c $(TEST_LOGGER) $(ROUTE_ROUTER_DIR)/hev-router.c $(ROUTE_ROUTER_DIR)/hev-radix-tree.c $(ROUTE_ROUTER_DIR)/hev-domain-hash.c

# Object files
RADIX_OBJ = $(RADIX_SRC:.c=.o)
DOMAIN_OBJ = $(DOMAIN_SRC:.c=.o)
IPV6_OBJ = $(IPV6_SRC:.c=.o)
ROUTER_OBJ = $(ROUTER_SRC:.c=.o)

# Default target
all: $(RADIX_TEST) $(DOMAIN_TEST) $(IPV6_TEST) # $(ROUTER_TEST)

# Build radix tree test
$(RADIX_TEST): $(RADIX_OBJ)
	$(CC) $(CFLAGS) -o $@ $^

# Build domain hash test
$(DOMAIN_TEST): $(DOMAIN_OBJ)
	$(CC) $(CFLAGS) -o $@ $^

# Build IPv6 test
$(IPV6_TEST): $(IPV6_OBJ)
	$(CC) $(CFLAGS) -o $@ $^

# Build router test
$(ROUTER_TEST): $(ROUTER_OBJ)
	$(CC) $(CFLAGS) -o $@ $^

# Run all tests
test: $(RADIX_TEST) $(DOMAIN_TEST) $(IPV6_TEST) # $(ROUTER_TEST)
	@echo "Running radix tree tests..."
	-./$(RADIX_TEST)
	@echo ""
	@echo "Running domain hash tests..."
	-./$(DOMAIN_TEST)
	@echo ""
	@echo "Running IPv6 tests..."
	-./$(IPV6_TEST)
	@echo ""
	@echo "Router tests temporarily disabled due to dependencies"

# Run individual tests
test-radix: $(RADIX_TEST)
	./$(RADIX_TEST)

test-domain: $(DOMAIN_TEST)
	./$(DOMAIN_TEST)

test-router: $(ROUTER_TEST)
	./$(ROUTER_TEST)

# Clean
clean:
	rm -f $(RADIX_OBJ) $(DOMAIN_OBJ) $(ROUTER_OBJ)
	rm -f $(RADIX_TEST) $(DOMAIN_TEST) $(ROUTER_TEST)

.PHONY: all test test-radix test-domain test-router clean