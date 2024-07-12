#include <stdio.h>
#include <stdlib.h>
#include <sys/fcntl.h>
#include <sys/mman.h>
#include <memory.h>
#include <unistd.h>
#include <stdint.h>

#define COLOR_RED     "\x1b[31m"
#define COLOR_GREEN   "\x1b[32m"
#define COLOR_YELLOW  "\x1b[33m"
#define COLOR_RESET   "\x1b[0m"

#define TAG_OK COLOR_GREEN "[+]" COLOR_RESET " "
#define TAG_FAIL COLOR_RED "[-]" COLOR_RESET " "
#define TAG_PROGRESS COLOR_YELLOW "[~]" COLOR_RESET " "


#include <ptedit_header.h>



int main(int argc, char *argv[]) {
  size_t address_pfn, target_pfn;

  if (argc != 3) {
    printf("Usage: %s <pid> <address>\n", argv[0]);
    return 1;
  }

  int pid = atoi(argv[1]);
  void *address = (void *)strtoull(argv[2], NULL, 16);

  if(ptedit_init()) {
    printf(TAG_FAIL "Could not initialize ptedit (did you load the kernel module?)\n");
    return 1;
  }

//   ptedit_use_implementation(PTEDIT_IMPL_KERNEL);

  char page[ptedit_get_pagesize()];

  printf(TAG_OK "address @ " COLOR_YELLOW "%p" COLOR_RESET "\n", address);

  ptedit_entry_t vm = ptedit_resolve(address, pid);
  if(vm.pgd == 0) {
    printf(TAG_FAIL "Could not resolve PTs\n");
    goto error;
  }
  ptedit_print_entry_t(vm);
  printf(TAG_PROGRESS "PTE PFN %zx\n", (size_t)(ptedit_cast(vm.pte, ptedit_pte_t).pfn));

  uint8_t key = (vm.pte >> 59) & 0b1111;

    printf(TAG_PROGRESS "PKEY %d\n",key);

error:
  ptedit_cleanup();

  return 0;
}