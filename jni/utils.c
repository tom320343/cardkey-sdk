#include <stdlib.h>
#include <stdio.h>
#include <time.h>
#include "utils.h"

void generate_random_id(char *buf, int size) {
    static int seeded = 0;
    if (!seeded) {
        srand(time(NULL) ^ (int)(long)buf);
        seeded = 1;
    }
    int id = 10000000 + rand() % 90000000;
    snprintf(buf, size, "%d", id);
}
