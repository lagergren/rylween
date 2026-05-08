use crate::json::{JsonValue, required, required_array, required_object, required_string};
use crate::sha256::sha256_hex;
use std::fs;
use std::path::PathBuf;

const EXPECTED_FORMAT: &str = "rylween.compiled.bundle";
const EXPECTED_SCHEMA_VERSION: &str = "3";
const SECTION_ORDER: &[&str] = &[
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
];

pub fn inspect_bundle(bundle: PathBuf) -> Result<(), String> {
    let source = fs::read_to_string(&bundle)
        .map_err(|error| format!("failed to read {}: {error}", bundle.display()))?;
    let parsed = crate::json::parse(&source)?;
    let root = parsed.as_object("compiled bundle root")?;

    let format = required_string(root, "format")?;
    let schema_version = required(root, "schemaVersion")?.as_number("schemaVersion")?;
    let content_hash = required_string(root, "bundleContentHash")?;
    let section_order = required_array(root, "sectionOrder")?;
    let sections = required_object(root, "sections")?;

    if format != EXPECTED_FORMAT {
        return Err(format!("unsupported bundle format '{format}'"));
    }
    if schema_version != EXPECTED_SCHEMA_VERSION {
        return Err(format!("unsupported bundle schemaVersion {schema_version}"));
    }
    if !is_sha256_hash(content_hash) {
        return Err("bundleContentHash must be sha256:<64 lowercase hex chars>".to_owned());
    }

    let declared_order = section_order
        .iter()
        .map(|value| value.as_string("sectionOrder entry").map(String::as_str))
        .collect::<Result<Vec<_>, _>>()?;
    if declared_order != SECTION_ORDER {
        return Err("sectionOrder does not match schema".to_owned());
    }

    let actual_order = sections
        .iter()
        .map(|(name, _)| name.as_str())
        .collect::<Vec<_>>();
    if actual_order != SECTION_ORDER {
        return Err("sections object order does not match schema".to_owned());
    }

    let sections_json = JsonValue::Object(sections.to_vec()).render_compact();
    let actual_hash = sha256_hex(sections_json.as_bytes());
    if content_hash != actual_hash {
        return Err(format!(
            "bundleContentHash does not match canonical sections SHA-256: expected {content_hash}, found {actual_hash}"
        ));
    }

    let summaries = SECTION_ORDER
        .iter()
        .map(|name| section_summary(name, required(sections, name)?))
        .collect::<Result<Vec<_>, _>>()?;

    println!("rylween-mud inspect");
    println!("bundle={}", bundle.display());
    println!("bytes={}", source.len());
    println!("format={format}");
    println!("schemaVersion={schema_version}");
    println!("bundleContentHash={content_hash}");
    println!("sections:");
    for summary in summaries {
        println!(
            "  {}: rows={} bytes={} fnv1a32={:08x}",
            summary.name, summary.rows, summary.bytes, summary.fnv1a32
        );
    }
    println!("status=experimental-k1.0");
    Ok(())
}

struct SectionSummary<'a> {
    name: &'a str,
    rows: usize,
    bytes: usize,
    fnv1a32: u32,
}

fn section_summary<'a>(name: &'a str, value: &JsonValue) -> Result<SectionSummary<'a>, String> {
    let compact = value.render_compact();
    Ok(SectionSummary {
        name,
        rows: value.row_count(),
        bytes: compact.len(),
        fnv1a32: fnv1a32(compact.as_bytes()),
    })
}

fn is_sha256_hash(value: &str) -> bool {
    let Some(hash) = value.strip_prefix("sha256:") else {
        return false;
    };
    hash.len() == 64
        && hash
            .chars()
            .all(|ch| ch.is_ascii_hexdigit() && !ch.is_ascii_uppercase())
}

fn fnv1a32(bytes: &[u8]) -> u32 {
    bytes.iter().fold(0x811c9dc5_u32, |hash, byte| {
        (hash ^ u32::from(*byte)).wrapping_mul(0x01000193)
    })
}
