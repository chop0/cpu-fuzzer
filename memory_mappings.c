#include "memory_mappings.h"
#include <stdio.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <string.h>

static void ensure_capacity(memory_mappings_t *mappings, size_t capacity) {
    if (mappings->capacity < capacity) {
        mappings->mappings = realloc(mappings->mappings, capacity * sizeof(memory_mapping_t));
        mappings->capacity = capacity;
    }
}

static void push_mapping(memory_mappings_t *mappings, memory_mapping_t mapping) {
    ensure_capacity(mappings, mappings->size + 1);
    mappings->mappings[mappings->size] = mapping;
    mappings->size++;
}

static int parse_permissions(const char *permissions) {
    int prot = 0;
    if (strchr(permissions, 'r')) {
        prot |= PROT_READ;
    }
    if (strchr(permissions, 'w')) {
        prot |= PROT_WRITE;
    }
    if (strchr(permissions, 'x')) {
        prot |= PROT_EXEC;
    }
    return prot;
}

void load_process_mappings(memory_mappings_t *mappings) {
    FILE *maps = fopen("/proc/self/maps", "r");
    if (!maps) {
        perror("fopen");
        exit(1);
    }

    mappings->size = 0;
    mappings->capacity = 0;
    mappings->mappings = NULL;

    char *line = NULL;
    size_t line_size = 0;
    while (getline(&line, &line_size, maps) != -1) {
        memory_mapping_t mapping;
        char permissions[5];
        if (sscanf(line, "%p-%*p %4s", &mapping.start, permissions) != 2) {
            continue;
        }
        mapping.length = (size_t)strtol(strchr(line, '-') + 1, NULL, 16) - (size_t)mapping.start;
        mapping.prot = parse_permissions(permissions);
        push_mapping(mappings, mapping);
    }

    free(line);
    fclose(maps);
}

void free_process_mappings(memory_mappings_t *mappings) {
    free(mappings->mappings);
    mappings->mappings = NULL;
    mappings->size = 0;
    mappings->capacity = 0;
}