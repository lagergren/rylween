#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

enum {
    BUNDLE_VERSION = 1U,
    BUNDLE_SECTION_COUNT = 25U
};

typedef enum {
    SECTION_UNIVERSE = 0U,
    SECTION_LIMITS = 1U,
    SECTION_STRINGS = 2U,
    SECTION_HANDLES = 3U,
    SECTION_FLAGS = 4U,
    SECTION_ITEMS = 5U,
    SECTION_ITEM_POOLS = 6U,
    SECTION_FORMULAS = 7U,
    SECTION_REWARDS = 8U,
    SECTION_TERRAIN = 9U,
    SECTION_WORLD_MAPS = 10U,
    SECTION_IMAGES = 11U,
    SECTION_STATIC_SCREENS = 12U,
    SECTION_SCREEN_REGIONS = 13U,
    SECTION_TOWNS = 14U,
    SECTION_BUILDING_SERVICES = 15U,
    SECTION_CHARACTERS = 16U,
    SECTION_MONSTERS = 17U,
    SECTION_MISSIONS = 18U,
    SECTION_SPELLS = 19U,
    SECTION_ITEM_USES = 20U,
    SECTION_DUNGEON_EVENTS = 21U,
    SECTION_DUNGEON_SCENARIOS = 22U,
    SECTION_SCRIPTS = 23U,
    SECTION_STATES = 24U
} BundleSectionId;

typedef struct {
    uint8_t *data;
    size_t size;
    size_t pos;
    uint32_t row_counts[BUNDLE_SECTION_COUNT];
    uint8_t seen[BUNDLE_SECTION_COUNT];
} Reader;

static void fail_at(const Reader *reader, const char *message) {
    fprintf(stderr, "compiled bundle loader failed at byte %zu: %s\n", reader->pos, message);
    exit(1);
}

static void require_available(Reader *reader, size_t count) {
    if (reader->pos > reader->size || count > reader->size - reader->pos) {
        fail_at(reader, "unexpected end of file");
    }
}

static uint32_t read_u32(Reader *reader) {
    require_available(reader, 4);
    uint32_t value = (uint32_t)reader->data[reader->pos]
        | ((uint32_t)reader->data[reader->pos + 1] << 8)
        | ((uint32_t)reader->data[reader->pos + 2] << 16)
        | ((uint32_t)reader->data[reader->pos + 3] << 24);
    reader->pos += 4;
    return value;
}

static uint32_t fnv1a32(const uint8_t *data, uint32_t size) {
    uint32_t hash = 2166136261U;
    for (uint32_t i = 0; i < size; ++i) {
        hash ^= data[i];
        hash *= 16777619U;
    }
    return hash;
}

static uint8_t *read_file(const char *path, size_t *size_out) {
    FILE *file = fopen(path, "rb");
    if (file == NULL) {
        fprintf(stderr, "cannot open %s\n", path);
        exit(1);
    }
    if (fseek(file, 0, SEEK_END) != 0) {
        fprintf(stderr, "cannot seek %s\n", path);
        exit(1);
    }
    long size = ftell(file);
    if (size < 0) {
        fprintf(stderr, "cannot size %s\n", path);
        exit(1);
    }
    rewind(file);
    uint8_t *data = malloc((size_t)size);
    if (data == NULL && size > 0) {
        fprintf(stderr, "out of memory\n");
        exit(1);
    }
    if (fread(data, 1, (size_t)size, file) != (size_t)size) {
        fprintf(stderr, "cannot read %s\n", path);
        exit(1);
    }
    fclose(file);
    *size_out = (size_t)size;
    return data;
}

int main(int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "usage: %s <rylween-bundle.rbin>\n", argv[0]);
        return 2;
    }

    Reader reader;
    memset(&reader, 0, sizeof(reader));
    reader.data = read_file(argv[1], &reader.size);

    const char magic[] = "RYLBNDL1";
    require_available(&reader, sizeof(magic) - 1);
    if (memcmp(reader.data, magic, sizeof(magic) - 1) != 0) {
        fail_at(&reader, "bad magic");
    }
    reader.pos += sizeof(magic) - 1;

    uint32_t version = read_u32(&reader);
    if (version != BUNDLE_VERSION) {
        fail_at(&reader, "unsupported bundle version");
    }

    uint32_t section_count = read_u32(&reader);
    if (section_count != BUNDLE_SECTION_COUNT) {
        fail_at(&reader, "unexpected section count");
    }

    for (uint32_t i = 0; i < section_count; ++i) {
        uint32_t section_id = read_u32(&reader);
        uint32_t row_count = read_u32(&reader);
        uint32_t byte_length = read_u32(&reader);
        uint32_t checksum = read_u32(&reader);
        if (section_id >= BUNDLE_SECTION_COUNT) {
            fail_at(&reader, "unknown section id");
        }
        if (reader.seen[section_id]) {
            fail_at(&reader, "duplicate section id");
        }
        require_available(&reader, byte_length);
        if (fnv1a32(reader.data + reader.pos, byte_length) != checksum) {
            fail_at(&reader, "section checksum mismatch");
        }
        reader.row_counts[section_id] = row_count;
        reader.seen[section_id] = 1U;
        reader.pos += byte_length;
    }

    for (uint32_t i = 0; i < BUNDLE_SECTION_COUNT; ++i) {
        if (!reader.seen[i]) {
            fail_at(&reader, "missing section");
        }
    }
    if (reader.pos != reader.size) {
        fail_at(&reader, "trailing bytes");
    }

    printf(
        "compiled bundle ok: strings=%u handles=%u scripts=%u states=%u sections=%u\n",
        reader.row_counts[SECTION_STRINGS],
        reader.row_counts[SECTION_HANDLES],
        reader.row_counts[SECTION_SCRIPTS],
        reader.row_counts[SECTION_STATES],
        section_count
    );

    free(reader.data);
    return 0;
}
