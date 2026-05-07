#include "json5.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

enum {
    BUNDLE_SCHEMA_VERSION = 3,
    RUNTIME_SYSTEMS_SCHEMA_VERSION = 1
};

static const char *SECTION_ORDER[] = {
    "universe",
    "limits",
    "strings",
    "handles",
    "flags",
    "items",
    "itemPools",
    "formulas",
    "rewards",
    "terrain",
    "worldMaps",
    "images",
    "staticScreens",
    "screenRegions",
    "towns",
    "buildingServices",
    "characters",
    "monsters",
    "missions",
    "spells",
    "itemUses",
    "dungeonEvents",
    "dungeonScenarios",
    "scripts",
    "runtimeSystems",
    "runtimeFacade",
    "states",
};

static const size_t SECTION_COUNT = sizeof SECTION_ORDER / sizeof SECTION_ORDER[0];

typedef struct {
    uint8_t data[64];
    uint32_t state[8];
    uint64_t bitlen;
    size_t datalen;
} sha256_ctx;

static void fail(const char *message) {
    fprintf(stderr, "native JSON bundle reader failed: %s\n", message);
    exit(1);
}

static const node *expect_kind(const node *n, node_kind kind, const char *label) {
    if (!n || n->kind != kind) {
        fprintf(stderr, "native JSON bundle reader failed: expected %s\n", label);
        exit(1);
    }
    return n;
}

static const node *req(const node *obj, const char *key, node_kind kind) {
    char label[160];
    snprintf(label, sizeof label, "%s", key);
    const node *value = obj_get(expect_kind(obj, N_OBJ, "object"), key);
    return expect_kind(value, kind, label);
}

static const char *req_str(const node *obj, const char *key) {
    return req(obj, key, N_STR)->u.sv;
}

static int32_t req_int(const node *obj, const char *key) {
    return req(obj, key, N_INT)->u.iv;
}

static const node *req_obj(const node *obj, const char *key) {
    return req(obj, key, N_OBJ);
}

static const node *req_arr(const node *obj, const char *key) {
    return req(obj, key, N_ARR);
}

static int str_eq(const char *a, const char *b) {
    return strcmp(a ? a : "", b ? b : "") == 0;
}

static uint32_t rotr32(uint32_t value, uint32_t bits) {
    return (value >> bits) | (value << (32U - bits));
}

static void sha256_transform(sha256_ctx *ctx, const uint8_t data[64]) {
    static const uint32_t k[64] = {
        0x428a2f98U, 0x71374491U, 0xb5c0fbcfU, 0xe9b5dba5U,
        0x3956c25bU, 0x59f111f1U, 0x923f82a4U, 0xab1c5ed5U,
        0xd807aa98U, 0x12835b01U, 0x243185beU, 0x550c7dc3U,
        0x72be5d74U, 0x80deb1feU, 0x9bdc06a7U, 0xc19bf174U,
        0xe49b69c1U, 0xefbe4786U, 0x0fc19dc6U, 0x240ca1ccU,
        0x2de92c6fU, 0x4a7484aaU, 0x5cb0a9dcU, 0x76f988daU,
        0x983e5152U, 0xa831c66dU, 0xb00327c8U, 0xbf597fc7U,
        0xc6e00bf3U, 0xd5a79147U, 0x06ca6351U, 0x14292967U,
        0x27b70a85U, 0x2e1b2138U, 0x4d2c6dfcU, 0x53380d13U,
        0x650a7354U, 0x766a0abbU, 0x81c2c92eU, 0x92722c85U,
        0xa2bfe8a1U, 0xa81a664bU, 0xc24b8b70U, 0xc76c51a3U,
        0xd192e819U, 0xd6990624U, 0xf40e3585U, 0x106aa070U,
        0x19a4c116U, 0x1e376c08U, 0x2748774cU, 0x34b0bcb5U,
        0x391c0cb3U, 0x4ed8aa4aU, 0x5b9cca4fU, 0x682e6ff3U,
        0x748f82eeU, 0x78a5636fU, 0x84c87814U, 0x8cc70208U,
        0x90befffaU, 0xa4506cebU, 0xbef9a3f7U, 0xc67178f2U
    };
    uint32_t m[64];
    for (size_t i = 0; i < 16; i++) {
        m[i] = ((uint32_t)data[i * 4] << 24) |
               ((uint32_t)data[i * 4 + 1] << 16) |
               ((uint32_t)data[i * 4 + 2] << 8) |
               (uint32_t)data[i * 4 + 3];
    }
    for (size_t i = 16; i < 64; i++) {
        uint32_t s0 = rotr32(m[i - 15], 7) ^ rotr32(m[i - 15], 18) ^ (m[i - 15] >> 3);
        uint32_t s1 = rotr32(m[i - 2], 17) ^ rotr32(m[i - 2], 19) ^ (m[i - 2] >> 10);
        m[i] = m[i - 16] + s0 + m[i - 7] + s1;
    }

    uint32_t a = ctx->state[0];
    uint32_t b = ctx->state[1];
    uint32_t c = ctx->state[2];
    uint32_t d = ctx->state[3];
    uint32_t e = ctx->state[4];
    uint32_t f = ctx->state[5];
    uint32_t g = ctx->state[6];
    uint32_t h = ctx->state[7];

    for (size_t i = 0; i < 64; i++) {
        uint32_t s1 = rotr32(e, 6) ^ rotr32(e, 11) ^ rotr32(e, 25);
        uint32_t ch = (e & f) ^ (~e & g);
        uint32_t temp1 = h + s1 + ch + k[i] + m[i];
        uint32_t s0 = rotr32(a, 2) ^ rotr32(a, 13) ^ rotr32(a, 22);
        uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
        uint32_t temp2 = s0 + maj;
        h = g;
        g = f;
        f = e;
        e = d + temp1;
        d = c;
        c = b;
        b = a;
        a = temp1 + temp2;
    }

    ctx->state[0] += a;
    ctx->state[1] += b;
    ctx->state[2] += c;
    ctx->state[3] += d;
    ctx->state[4] += e;
    ctx->state[5] += f;
    ctx->state[6] += g;
    ctx->state[7] += h;
}

static void sha256_init(sha256_ctx *ctx) {
    ctx->datalen = 0;
    ctx->bitlen = 0;
    ctx->state[0] = 0x6a09e667U;
    ctx->state[1] = 0xbb67ae85U;
    ctx->state[2] = 0x3c6ef372U;
    ctx->state[3] = 0xa54ff53aU;
    ctx->state[4] = 0x510e527fU;
    ctx->state[5] = 0x9b05688cU;
    ctx->state[6] = 0x1f83d9abU;
    ctx->state[7] = 0x5be0cd19U;
}

static void sha256_update(sha256_ctx *ctx, const void *data, size_t len) {
    const uint8_t *bytes = (const uint8_t *)data;
    for (size_t i = 0; i < len; i++) {
        ctx->data[ctx->datalen++] = bytes[i];
        if (ctx->datalen == sizeof ctx->data) {
            sha256_transform(ctx, ctx->data);
            ctx->bitlen += 512;
            ctx->datalen = 0;
        }
    }
}

static void sha256_final(sha256_ctx *ctx, uint8_t hash[32]) {
    uint64_t bitlen = ctx->bitlen + (uint64_t)ctx->datalen * 8U;
    size_t i = ctx->datalen;
    ctx->data[i++] = 0x80U;

    if (i > 56) {
        while (i < 64) {
            ctx->data[i++] = 0;
        }
        sha256_transform(ctx, ctx->data);
        i = 0;
    }
    while (i < 56) {
        ctx->data[i++] = 0;
    }
    for (size_t j = 0; j < 8; j++) {
        ctx->data[63 - j] = (uint8_t)(bitlen >> (j * 8));
    }
    sha256_transform(ctx, ctx->data);

    for (size_t j = 0; j < 4; j++) {
        for (size_t s = 0; s < 8; s++) {
            hash[j + s * 4] = (uint8_t)(ctx->state[s] >> (24 - j * 8));
        }
    }
}

static void hash_text(sha256_ctx *ctx, const char *text) {
    sha256_update(ctx, text, strlen(text));
}

static void hash_node(sha256_ctx *ctx, const node *n);

static void hash_quoted_json(sha256_ctx *ctx, const char *value) {
    static const char hex[] = "0123456789abcdef";
    hash_text(ctx, "\"");
    for (const unsigned char *p = (const unsigned char *)(value ? value : ""); *p; p++) {
        switch (*p) {
            case '\\':
                hash_text(ctx, "\\\\");
                break;
            case '"':
                hash_text(ctx, "\\\"");
                break;
            case '\b':
                hash_text(ctx, "\\b");
                break;
            case '\f':
                hash_text(ctx, "\\f");
                break;
            case '\n':
                hash_text(ctx, "\\n");
                break;
            case '\r':
                hash_text(ctx, "\\r");
                break;
            case '\t':
                hash_text(ctx, "\\t");
                break;
            default:
                if (*p < 0x20U) {
                    char escaped[7] = {'\\', 'u', '0', '0', hex[*p >> 4], hex[*p & 0x0fU], '\0'};
                    hash_text(ctx, escaped);
                } else {
                    sha256_update(ctx, p, 1);
                }
                break;
        }
    }
    hash_text(ctx, "\"");
}

static void hash_node(sha256_ctx *ctx, const node *n) {
    char number[32];
    if (!n) {
        fail("expected JSON value");
    }
    switch (n->kind) {
        case N_NULL:
            hash_text(ctx, "null");
            break;
        case N_BOOL:
            hash_text(ctx, n->u.bv ? "true" : "false");
            break;
        case N_INT:
            snprintf(number, sizeof number, "%d", n->u.iv);
            hash_text(ctx, number);
            break;
        case N_STR:
            hash_quoted_json(ctx, n->u.sv);
            break;
        case N_ARR:
            hash_text(ctx, "[");
            for (size_t i = 0; i < n->u.arr.n; i++) {
                if (i > 0) {
                    hash_text(ctx, ",");
                }
                hash_node(ctx, n->u.arr.items[i]);
            }
            hash_text(ctx, "]");
            break;
        case N_OBJ:
            hash_text(ctx, "{");
            for (size_t i = 0; i < n->u.obj.n; i++) {
                if (i > 0) {
                    hash_text(ctx, ",");
                }
                hash_quoted_json(ctx, n->u.obj.items[i].key);
                hash_text(ctx, ":");
                hash_node(ctx, n->u.obj.items[i].val);
            }
            hash_text(ctx, "}");
            break;
    }
}

static void expect_string_value(const node *n, const char *expected, const char *label) {
    expect_kind(n, N_STR, label);
    if (!str_eq(n->u.sv, expected)) {
        fprintf(stderr, "native JSON bundle reader failed: expected %s '%s', found '%s'\n",
                label, expected, n->u.sv ? n->u.sv : "");
        exit(1);
    }
}

static const node *find_obj_by_string_field(const node *arr, const char *field, const char *value) {
    expect_kind(arr, N_ARR, "array");
    for (size_t i = 0; i < arr->u.arr.n; i++) {
        const node *row = expect_kind(arr->u.arr.items[i], N_OBJ, "array object");
        const node *field_node = obj_get(row, field);
        if (field_node && field_node->kind == N_STR && str_eq(field_node->u.sv, value)) {
            return row;
        }
    }
    return NULL;
}

static void reject_reflective_type_markers(const node *n, const char *path) {
    if (!n) {
        return;
    }
    if (n->kind == N_OBJ) {
        for (size_t i = 0; i < n->u.obj.n; i++) {
            if (str_eq(n->u.obj.items[i].key, "_type")) {
                fprintf(stderr, "native JSON bundle reader failed: %s contains reflective _type marker\n", path);
                exit(1);
            }
            reject_reflective_type_markers(n->u.obj.items[i].val, n->u.obj.items[i].key);
        }
    } else if (n->kind == N_ARR) {
        for (size_t i = 0; i < n->u.arr.n; i++) {
            reject_reflective_type_markers(n->u.arr.items[i], path);
        }
    }
}

static void validate_section_order(const node *root, const node *sections) {
    const node *order = req_arr(root, "sectionOrder");
    if (order->u.arr.n != SECTION_COUNT) {
        fail("sectionOrder has unexpected length");
    }
    if (sections->u.obj.n != SECTION_COUNT) {
        fail("sections object has unexpected length");
    }
    for (size_t i = 0; i < SECTION_COUNT; i++) {
        expect_string_value(order->u.arr.items[i], SECTION_ORDER[i], "sectionOrder entry");
        if (!str_eq(sections->u.obj.items[i].key, SECTION_ORDER[i])) {
            fail("sections object order does not match schema");
        }
    }
}

static size_t array_count(const node *sections, const char *name) {
    return req_arr(sections, name)->u.arr.n;
}

static void validate_hash_shape(const char *hash) {
    if (!hash || strncmp(hash, "sha256:", 7) != 0 || strlen(hash) != 71) {
        fail("bundleContentHash must be a sha256 hex string");
    }
    for (const char *p = hash + 7; *p; p++) {
        int hex = (*p >= '0' && *p <= '9') ||
                  (*p >= 'a' && *p <= 'f') ||
                  (*p >= 'A' && *p <= 'F');
        if (!hex) {
            fail("bundleContentHash contains non-hex characters");
        }
    }
}

static void validate_canonical_sections_hash(const node *root, const node *sections) {
    const char *expected = req_str(root, "bundleContentHash");
    validate_hash_shape(expected);

    sha256_ctx ctx;
    uint8_t digest[32];
    char actual[65];
    sha256_init(&ctx);
    hash_node(&ctx, sections);
    sha256_final(&ctx, digest);
    for (size_t i = 0; i < sizeof digest; i++) {
        snprintf(actual + i * 2, 3, "%02x", digest[i]);
    }
    actual[64] = '\0';

    if (!str_eq(expected + 7, actual)) {
        fail("bundleContentHash does not match canonical sections SHA-256");
    }
}

static void require_command_group(const node *commands, const char *key) {
    if (!obj_get(commands, key)) {
        fprintf(stderr, "native JSON bundle reader failed: runtimeSystems.commands missing %s\n", key);
        exit(1);
    }
}

static void validate_commands(const node *commands, const node *castles) {
    expect_kind(commands, N_OBJ, "runtimeSystems.commands");
    require_command_group(commands, "wilderness");
    require_command_group(commands, "dungeon");
    expect_kind(castles, N_ARR, "runtimeSystems.castles");
    for (size_t i = 0; i < castles->u.arr.n; i++) {
        const node *castle = expect_kind(castles->u.arr.items[i], N_OBJ, "castle object");
        const char *id = req_str(castle, "id");
        char key[192];
        snprintf(key, sizeof key, "castle.%s.navigation", id);
        require_command_group(commands, key);
        snprintf(key, sizeof key, "castle.%s.treasure", id);
        require_command_group(commands, key);
        snprintf(key, sizeof key, "castle.%s.stairs", id);
        require_command_group(commands, key);
    }
}

static void validate_prompt_groups(const node *prompts) {
    const node *groups = req_arr(prompts, "groups");
    if (!find_obj_by_string_field(groups, "group", "wilderness")) {
        fail("runtimeSystems.prompts missing wilderness group");
    }
    if (!find_obj_by_string_field(groups, "group", "dungeon")) {
        fail("runtimeSystems.prompts missing dungeon group");
    }
}

static void validate_castles(const node *castles) {
    expect_kind(castles, N_ARR, "runtimeSystems.castles");
    if (castles->u.arr.n == 0) {
        fail("runtimeSystems.castles is empty");
    }
    for (size_t i = 0; i < castles->u.arr.n; i++) {
        const node *castle = expect_kind(castles->u.arr.items[i], N_OBJ, "castle object");
        const char *id = req_str(castle, "id");
        if (!str_eq(req_str(castle, "kind"), "gridCastle")) {
            fprintf(stderr, "native JSON bundle reader failed: runtimeSystems.castles.%s kind must be gridCastle\n", id);
            exit(1);
        }
        (void)req_obj(castle, "entry");
        (void)req_arr(castle, "roomEvents");
    }
}

static void validate_runtime_systems(const node *sections) {
    const node *systems = req_obj(sections, "runtimeSystems");
    const node *castles = req_arr(systems, "castles");
    reject_reflective_type_markers(systems, "runtimeSystems");
    if (req_int(systems, "schemaVersion") != RUNTIME_SYSTEMS_SCHEMA_VERSION) {
        fail("runtimeSystems schemaVersion mismatch");
    }
    validate_commands(req_obj(systems, "commands"), castles);
    validate_prompt_groups(req_obj(systems, "prompts"));
    validate_castles(castles);
}

int main(int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "usage: %s <rylween-bundle.json>\n", argv[0]);
        return 2;
    }

    node *root_node = json5_parse_file(argv[1]);
    const node *root = expect_kind(root_node, N_OBJ, "bundle root");
    if (!str_eq(req_str(root, "format"), "rylween.compiled.bundle")) {
        fail("format mismatch");
    }
    if (req_int(root, "schemaVersion") != BUNDLE_SCHEMA_VERSION) {
        fail("bundle schemaVersion mismatch");
    }
    const node *sections = req_obj(root, "sections");
    validate_canonical_sections_hash(root, sections);
    validate_section_order(root, sections);
    validate_runtime_systems(sections);

    size_t strings = array_count(sections, "strings");
    size_t handles = array_count(sections, "handles");
    size_t scripts = array_count(sections, "scripts");
    size_t states = array_count(sections, "states");
    if (strings == 0 || handles == 0 || scripts == 0 || states == 0) {
        fail("core table sections must not be empty");
    }

    printf("native json ok: strings=%zu handles=%zu scripts=%zu states=%zu runtimeSystems=%d castles=%zu\n",
           strings,
           handles,
           scripts,
           states,
           RUNTIME_SYSTEMS_SCHEMA_VERSION,
           req_arr(req_obj(sections, "runtimeSystems"), "castles")->u.arr.n);

    node_free(root_node);
    return 0;
}
