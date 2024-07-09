#ifndef MEMORY_MAPPINGS_H
#define MEMORY_MAPPINGS_H

#include <stddef.h>

typedef struct {
    void *start;
    size_t length;
    int prot;
} memory_mapping_t;

typedef struct {
    size_t size;
    size_t capacity;
    memory_mapping_t *mappings;
} memory_mappings_t;

void load_process_mappings(memory_mappings_t *mappings);
void free_process_mappings(memory_mappings_t *mappings);

#endif