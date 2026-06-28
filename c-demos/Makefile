# ConceptualWare — C/C++ Memory Concepts Demo
# Demonstrates: pointers, malloc/free, smart pointers, RAII, CoW, cache, memory layout

CC      = gcc
CXX     = g++
CFLAGS  = -std=c11   -Wall -Wextra -g -fsanitize=address,undefined
CXXFLAGS= -std=c++17 -Wall -Wextra -g -fsanitize=address,undefined
LDFLAGS = -lm

.PHONY: all clean run

TARGETS = 01_pointers 02_memory_management 03_smart_pointers

all: $(TARGETS)

01_pointers: 01_pointers.c
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $<

02_memory_management: 02_memory_management.c
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $<

03_smart_pointers: 03_smart_pointers.cpp
	$(CXX) $(CXXFLAGS) -o $@ $<

run: all
	@echo "════════════════════════════════════"
	@echo "Running 01_pointers..."
	@echo "════════════════════════════════════"
	./01_pointers
	@echo ""
	@echo "════════════════════════════════════"
	@echo "Running 02_memory_management..."
	@echo "════════════════════════════════════"
	./02_memory_management
	@echo ""
	@echo "════════════════════════════════════"
	@echo "Running 03_smart_pointers..."
	@echo "════════════════════════════════════"
	./03_smart_pointers

clean:
	rm -f $(TARGETS)
