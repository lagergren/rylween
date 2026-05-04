#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct {
    uint32_t kind;
    uint32_t id;
} HandleRef;

typedef struct {
    uint8_t *data;
    size_t size;
    size_t pos;
    uint32_t handle_kind_count;
    uint32_t opcode_count;
    uint32_t runtime_text_part_count;
    uint32_t string_count;
    uint32_t handle_count;
    HandleRef *handles;
    uint64_t instruction_count;
    uint64_t operand_count;
} Reader;

static void fail_at(const Reader *reader, const char *message) {
    fprintf(stderr, "native smoke loader failed at byte %zu: %s\n", reader->pos, message);
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

static void read_bool(Reader *reader) {
    uint32_t value = read_u32(reader);
    if (value > 1U) {
        fail_at(reader, "boolean value is not 0/1");
    }
}

static void skip_bytes(Reader *reader, uint32_t count) {
    require_available(reader, count);
    reader->pos += count;
}

static void read_inline_string(Reader *reader) {
    skip_bytes(reader, read_u32(reader));
}

static void read_string_ref(Reader *reader) {
    uint32_t id = read_u32(reader);
    if (id == 0U || id > reader->string_count) {
        fail_at(reader, "string id is out of range");
    }
}

static int handle_exists(const Reader *reader, HandleRef ref) {
    for (uint32_t i = 0; i < reader->handle_count; ++i) {
        if (reader->handles[i].kind == ref.kind && reader->handles[i].id == ref.id) {
            return 1;
        }
    }
    return 0;
}

static HandleRef read_handle_ref(Reader *reader) {
    HandleRef ref;
    ref.kind = read_u32(reader);
    ref.id = read_u32(reader);
    if (ref.kind >= reader->handle_kind_count) {
        fail_at(reader, "handle kind is out of range");
    }
    if (!handle_exists(reader, ref)) {
        fail_at(reader, "handle ref is not declared");
    }
    return ref;
}

static void parse_instructions(Reader *reader);
static void parse_text_block(Reader *reader);
static void parse_int_expr(Reader *reader);
static void parse_bool_expr(Reader *reader);
static void parse_string_expr(Reader *reader);
static void parse_lvalue(Reader *reader);
static void parse_view_command(Reader *reader);
static void parse_input_binding(Reader *reader);
static void parse_script_reward(Reader *reader);
static void parse_random_item_offer(Reader *reader);
static void parse_shop_service(Reader *reader);

static void parse_string_id_list(Reader *reader) {
    uint32_t count = read_u32(reader);
    for (uint32_t i = 0; i < count; ++i) {
        read_string_ref(reader);
    }
}

static void parse_handle_ref_list(Reader *reader) {
    uint32_t count = read_u32(reader);
    for (uint32_t i = 0; i < count; ++i) {
        (void)read_handle_ref(reader);
    }
}

static void parse_optional_int(Reader *reader) {
    uint32_t present = read_u32(reader);
    if (present > 1U) {
        fail_at(reader, "optional marker is not 0/1");
    }
    if (present) {
        (void)read_u32(reader);
    }
}

static void parse_optional_string_ref(Reader *reader) {
    uint32_t present = read_u32(reader);
    if (present > 1U) {
        fail_at(reader, "optional marker is not 0/1");
    }
    if (present) {
        read_string_ref(reader);
    }
}

static void parse_optional_text_block(Reader *reader) {
    uint32_t present = read_u32(reader);
    if (present > 1U) {
        fail_at(reader, "optional marker is not 0/1");
    }
    if (present) {
        parse_text_block(reader);
    }
}

static void parse_optional_int_expr(Reader *reader) {
    uint32_t present = read_u32(reader);
    if (present > 1U) {
        fail_at(reader, "optional marker is not 0/1");
    }
    if (present) {
        parse_int_expr(reader);
    }
}

static void parse_enum_name(Reader *reader) {
    read_inline_string(reader);
}

static void parse_reward_application(Reader *reader) {
    parse_enum_name(reader);
    parse_enum_name(reader);
    parse_enum_name(reader);
}

static void parse_reward_messages(Reader *reader) {
    parse_optional_text_block(reader);
    parse_optional_text_block(reader);
}

static void parse_text_part(Reader *reader) {
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case 0: read_string_ref(reader); break;
        case 1: parse_int_expr(reader); break;
        case 2: parse_string_expr(reader); break;
        case 3:
        case 4:
        case 5:
        case 7:
            (void)read_handle_ref(reader);
            break;
        case 6:
            (void)read_handle_ref(reader);
            (void)read_u32(reader);
            break;
        case 8: {
            uint32_t runtime_part = read_u32(reader);
            if (runtime_part >= reader->runtime_text_part_count) {
                fail_at(reader, "runtime text part is out of range");
            }
            break;
        }
        default:
            fail_at(reader, "unknown text part tag");
    }
}

static void parse_text_line(Reader *reader) {
    uint32_t count = read_u32(reader);
    for (uint32_t i = 0; i < count; ++i) {
        parse_text_part(reader);
    }
}

static void parse_text_block(Reader *reader) {
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case 0:
            (void)read_handle_ref(reader);
            break;
        case 1:
            parse_string_id_list(reader);
            break;
        case 2: {
            uint32_t count = read_u32(reader);
            for (uint32_t i = 0; i < count; ++i) {
                parse_text_line(reader);
            }
            break;
        }
        default:
            fail_at(reader, "unknown text block tag");
    }
}

static void parse_view_command(Reader *reader) {
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case 0:
        case 1:
        case 2:
        case 3:
            break;
        case 4:
            (void)read_handle_ref(reader);
            break;
        default:
            fail_at(reader, "unknown view command tag");
    }
}

static void parse_int_expr(Reader *reader) {
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case 0:
            (void)read_u32(reader);
            break;
        case 1:
        case 7:
        case 8:
        case 9:
        case 10:
            read_string_ref(reader);
            break;
        case 2:
            parse_int_expr(reader);
            parse_enum_name(reader);
            parse_int_expr(reader);
            break;
        case 3:
            parse_bool_expr(reader);
            parse_int_expr(reader);
            parse_int_expr(reader);
            break;
        case 4:
            parse_int_expr(reader);
            parse_int_expr(reader);
            break;
        case 5:
        case 6:
            parse_int_expr(reader);
            read_string_ref(reader);
            break;
        case 11:
            (void)read_handle_ref(reader);
            break;
        case 12:
            parse_int_expr(reader);
            break;
        case 13:
            read_string_ref(reader);
            parse_string_id_list(reader);
            break;
        default:
            fail_at(reader, "unknown int expr tag");
    }
}

static void parse_bool_expr(Reader *reader) {
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case 0:
            parse_int_expr(reader);
            parse_enum_name(reader);
            parse_int_expr(reader);
            break;
        case 1:
            parse_bool_expr(reader);
            break;
        case 2:
        case 3:
            parse_bool_expr(reader);
            parse_bool_expr(reader);
            break;
        case 4:
            (void)read_handle_ref(reader);
            (void)read_handle_ref(reader);
            break;
        case 5:
            (void)read_handle_ref(reader);
            break;
        case 6:
        case 7:
            parse_int_expr(reader);
            read_string_ref(reader);
            break;
        case 8:
        case 9:
            read_string_ref(reader);
            break;
        default:
            fail_at(reader, "unknown bool expr tag");
    }
}

static void parse_native_string_arg(Reader *reader) {
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case 0:
            read_string_ref(reader);
            break;
        case 1:
            parse_int_expr(reader);
            break;
        default:
            fail_at(reader, "unknown native string arg tag");
    }
}

static void parse_string_expr(Reader *reader) {
    uint32_t tag = read_u32(reader);
    if (tag != 0U) {
        fail_at(reader, "unknown string expr tag");
    }
    read_string_ref(reader);
    uint32_t count = read_u32(reader);
    for (uint32_t i = 0; i < count; ++i) {
        parse_native_string_arg(reader);
    }
}

static void parse_lvalue(Reader *reader) {
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case 0:
        case 1:
        case 4:
            read_string_ref(reader);
            break;
        case 2:
        case 3:
            parse_int_expr(reader);
            read_string_ref(reader);
            break;
        default:
            fail_at(reader, "unknown lvalue tag");
    }
}

static void parse_input_binding(Reader *reader) {
    read_string_ref(reader);
    parse_string_id_list(reader);
    read_string_ref(reader);
}

static void parse_switch_cases(Reader *reader) {
    uint32_t count = read_u32(reader);
    for (uint32_t i = 0; i < count; ++i) {
        parse_optional_int(reader);
        parse_instructions(reader);
    }
}

static void parse_menu_options(Reader *reader) {
    uint32_t count = read_u32(reader);
    for (uint32_t i = 0; i < count; ++i) {
        parse_input_binding(reader);
        parse_instructions(reader);
    }
}

static void parse_input_bindings(Reader *reader) {
    uint32_t count = read_u32(reader);
    for (uint32_t i = 0; i < count; ++i) {
        parse_input_binding(reader);
    }
}

static void parse_text_answer(Reader *reader) {
    parse_string_id_list(reader);
    parse_instructions(reader);
    parse_instructions(reader);
    read_bool(reader);
    read_bool(reader);
    parse_optional_int(reader);
}

static void parse_script_reward(Reader *reader) {
    parse_optional_int_expr(reader);
    parse_handle_ref_list(reader);
    parse_reward_application(reader);
    parse_reward_messages(reader);
}

static void parse_random_item_offer(Reader *reader) {
    (void)read_handle_ref(reader);
    parse_text_block(reader);
    parse_text_block(reader);
    parse_text_block(reader);
    parse_text_block(reader);
    parse_optional_text_block(reader);
    parse_reward_application(reader);
    parse_reward_messages(reader);
    parse_input_binding(reader);
    parse_input_binding(reader);
    parse_optional_int(reader);
    (void)read_u32(reader);
    (void)read_u32(reader);
    (void)read_u32(reader);
    (void)read_u32(reader);
}

static void parse_shop_price(Reader *reader) {
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case 0:
        case 1:
            break;
        case 2:
        case 3:
        case 4:
            (void)read_u32(reader);
            break;
        default:
            fail_at(reader, "unknown shop price tag");
    }
}

static void parse_shop_quantity_cap(Reader *reader) {
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case 0:
            parse_enum_name(reader);
            (void)read_u32(reader);
            break;
        case 1:
            parse_enum_name(reader);
            break;
        default:
            fail_at(reader, "unknown shop quantity cap tag");
    }
}

static void parse_shop_purchase(Reader *reader) {
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case 0:
            break;
        case 1:
            parse_enum_name(reader);
            parse_text_block(reader);
            parse_shop_quantity_cap(reader);
            parse_text_block(reader);
            parse_enum_name(reader);
            break;
        default:
            fail_at(reader, "unknown shop purchase tag");
    }
}

static void parse_shop_item_offer(Reader *reader) {
    parse_input_binding(reader);
    (void)read_handle_ref(reader);
    parse_optional_string_ref(reader);
    parse_optional_text_block(reader);
    parse_shop_price(reader);
    parse_shop_purchase(reader);
}

static void parse_shop_catalog_page(Reader *reader) {
    uint32_t entry_count = read_u32(reader);
    for (uint32_t i = 0; i < entry_count; ++i) {
        parse_shop_item_offer(reader);
    }
    parse_optional_text_block(reader);
}

static void parse_shop_catalog(Reader *reader) {
    parse_input_binding(reader);
    read_string_ref(reader);
    parse_optional_text_block(reader);
    uint32_t page_count = read_u32(reader);
    for (uint32_t i = 0; i < page_count; ++i) {
        parse_shop_catalog_page(reader);
    }
}

static void parse_shop_buy(Reader *reader) {
    parse_text_block(reader);
    uint32_t catalog_count = read_u32(reader);
    for (uint32_t i = 0; i < catalog_count; ++i) {
        parse_shop_catalog(reader);
    }
    parse_text_block(reader);
    parse_text_block(reader);
    parse_text_block(reader);
    parse_optional_text_block(reader);
    parse_optional_text_block(reader);
    parse_text_block(reader);
    parse_input_binding(reader);
    parse_input_binding(reader);
    parse_reward_application(reader);
}

static void parse_shop_bargain(Reader *reader) {
    uint32_t bonus_count = read_u32(reader);
    for (uint32_t i = 0; i < bonus_count; ++i) {
        (void)read_u32(reader);
        (void)read_u32(reader);
    }
    parse_optional_int(reader);
}

static void parse_optional_shop_bargain(Reader *reader) {
    uint32_t present = read_u32(reader);
    if (present > 1U) {
        fail_at(reader, "optional marker is not 0/1");
    }
    if (present) {
        parse_shop_bargain(reader);
    }
}

static void parse_shop_sell(Reader *reader) {
    parse_text_block(reader);
    uint32_t accepted_kind_count = read_u32(reader);
    for (uint32_t i = 0; i < accepted_kind_count; ++i) {
        parse_enum_name(reader);
    }
    parse_handle_ref_list(reader);
    parse_handle_ref_list(reader);
    parse_text_block(reader);
    parse_text_block(reader);
    parse_optional_text_block(reader);
    parse_shop_price(reader);
    parse_optional_shop_bargain(reader);
    parse_input_binding(reader);
    parse_input_binding(reader);
}

static void parse_shop_interlude(Reader *reader) {
    (void)read_u32(reader);
    parse_text_block(reader);
    parse_input_binding(reader);
    parse_input_binding(reader);
    uint32_t accepted_count = read_u32(reader);
    for (uint32_t i = 0; i < accepted_count; ++i) {
        parse_text_block(reader);
    }
    parse_text_block(reader);
    read_bool(reader);
}

static void parse_optional_shop_buy(Reader *reader) {
    uint32_t present = read_u32(reader);
    if (present > 1U) {
        fail_at(reader, "optional marker is not 0/1");
    }
    if (present) {
        parse_shop_buy(reader);
    }
}

static void parse_optional_shop_sell(Reader *reader) {
    uint32_t present = read_u32(reader);
    if (present > 1U) {
        fail_at(reader, "optional marker is not 0/1");
    }
    if (present) {
        parse_shop_sell(reader);
    }
}

static void parse_optional_shop_interlude(Reader *reader) {
    uint32_t present = read_u32(reader);
    if (present > 1U) {
        fail_at(reader, "optional marker is not 0/1");
    }
    if (present) {
        parse_shop_interlude(reader);
    }
}

static void parse_shop_service(Reader *reader) {
    (void)read_handle_ref(reader);
    (void)read_handle_ref(reader);
    parse_text_block(reader);
    parse_text_block(reader);
    parse_text_block(reader);
    parse_input_binding(reader);
    parse_input_binding(reader);
    parse_input_binding(reader);
    parse_text_block(reader);
    parse_optional_shop_buy(reader);
    parse_optional_shop_sell(reader);
    parse_optional_shop_interlude(reader);
}

static void parse_operand(Reader *reader) {
    ++reader->operand_count;
    uint32_t tag = read_u32(reader);
    switch (tag) {
        case 0:
            (void)read_u32(reader);
            break;
        case 1:
            read_bool(reader);
            break;
        case 2:
            (void)read_u32(reader);
            break;
        case 3:
            read_string_ref(reader);
            break;
        case 4:
            parse_string_id_list(reader);
            break;
        case 5:
            (void)read_handle_ref(reader);
            break;
        case 6:
            parse_handle_ref_list(reader);
            break;
        case 7:
            parse_text_block(reader);
            break;
        case 8:
            parse_int_expr(reader);
            break;
        case 9:
            parse_bool_expr(reader);
            break;
        case 10:
            parse_string_expr(reader);
            break;
        case 11:
            parse_lvalue(reader);
            break;
        case 12:
            parse_view_command(reader);
            break;
        case 13:
            parse_instructions(reader);
            break;
        case 14:
            parse_switch_cases(reader);
            break;
        case 15:
            parse_menu_options(reader);
            break;
        case 16:
            parse_input_bindings(reader);
            break;
        case 17:
            parse_text_answer(reader);
            break;
        case 18:
            parse_script_reward(reader);
            break;
        case 19:
            parse_random_item_offer(reader);
            break;
        case 20:
            parse_shop_service(reader);
            break;
        case 21:
            read_inline_string(reader);
            break;
        default:
            fail_at(reader, "unknown operand tag");
    }
}

static void parse_instructions(Reader *reader) {
    uint32_t count = read_u32(reader);
    for (uint32_t i = 0; i < count; ++i) {
        uint32_t opcode = read_u32(reader);
        if (opcode >= reader->opcode_count) {
            fail_at(reader, "opcode is out of range");
        }
        ++reader->instruction_count;
        uint32_t operand_count = read_u32(reader);
        for (uint32_t j = 0; j < operand_count; ++j) {
            parse_operand(reader);
        }
    }
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
        fprintf(stderr, "usage: %s <rylween-native-smoke.rsmoke>\n", argv[0]);
        return 2;
    }

    Reader reader;
    memset(&reader, 0, sizeof(reader));
    reader.data = read_file(argv[1], &reader.size);

    const char magic[] = "RYLSMOKE";
    require_available(&reader, sizeof(magic) - 1);
    if (memcmp(reader.data, magic, sizeof(magic) - 1) != 0) {
        fail_at(&reader, "bad magic");
    }
    reader.pos += sizeof(magic) - 1;

    uint32_t version = read_u32(&reader);
    if (version != 1U) {
        fail_at(&reader, "unsupported artifact version");
    }
    reader.handle_kind_count = read_u32(&reader);
    reader.opcode_count = read_u32(&reader);
    reader.runtime_text_part_count = read_u32(&reader);

    reader.string_count = read_u32(&reader);
    for (uint32_t i = 0; i < reader.string_count; ++i) {
        skip_bytes(&reader, read_u32(&reader));
    }

    reader.handle_count = read_u32(&reader);
    reader.handles = calloc(reader.handle_count, sizeof(HandleRef));
    if (reader.handles == NULL && reader.handle_count > 0U) {
        fail_at(&reader, "out of memory");
    }
    for (uint32_t i = 0; i < reader.handle_count; ++i) {
        HandleRef ref;
        ref.kind = read_u32(&reader);
        ref.id = read_u32(&reader);
        if (ref.kind >= reader.handle_kind_count) {
            fail_at(&reader, "declared handle kind is out of range");
        }
        reader.handles[i] = ref;
        (void)read_u32(&reader);
        read_string_ref(&reader);
    }

    uint32_t script_count = read_u32(&reader);
    for (uint32_t i = 0; i < script_count; ++i) {
        (void)read_handle_ref(&reader);
        parse_instructions(&reader);
    }

    uint32_t state_count = read_u32(&reader);
    if (reader.pos != reader.size) {
        fail_at(&reader, "trailing bytes");
    }

    printf(
        "native smoke ok: strings=%u handles=%u scripts=%u states=%u instructions=%llu operands=%llu\n",
        reader.string_count,
        reader.handle_count,
        script_count,
        state_count,
        (unsigned long long)reader.instruction_count,
        (unsigned long long)reader.operand_count
    );

    free(reader.handles);
    free(reader.data);
    return 0;
}
