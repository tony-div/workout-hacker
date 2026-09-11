pub mod rf_model;

use rf_model::RandomForestRunner;
use std::collections::HashMap;
use std::ffi::{c_char, c_double, c_int, CStr, CString};
use std::sync::{Mutex, OnceLock};

#[cfg(target_os = "android")]
extern "C" {
    fn __android_log_write(prio: c_int, tag: *const c_char, text: *const c_char) -> c_int;
}

fn log_debug(message: &str) {
    #[cfg(target_os = "android")]
    {
        const ANDROID_LOG_DEBUG: c_int = 3;
        const TAG: &[u8] = b"NitroRandForest\0";
        let safe_message = message.replace('\0', "\\0");
        if let Ok(c_message) = CString::new(safe_message) {
            unsafe {
                let _ = __android_log_write(
                    ANDROID_LOG_DEBUG,
                    TAG.as_ptr() as *const c_char,
                    c_message.as_ptr(),
                );
            }
        }
    }

    #[cfg(not(target_os = "android"))]
    {
        eprintln!("[NitroRandForest] {message}");
    }
}

const DEFAULT_MODEL: &str = "default";

struct RfState {
    models: HashMap<String, RandomForestRunner>,
}

impl Default for RfState {
    fn default() -> Self {
        Self { models: HashMap::new() }
    }
}

fn state() -> &'static Mutex<RfState> {
    static STATE: OnceLock<Mutex<RfState>> = OnceLock::new();
    STATE.get_or_init(|| Mutex::new(RfState::default()))
}

fn cstr_to_string(ptr: *const c_char) -> Option<String> {
    if ptr.is_null() {
        return None;
    }
    let cstr = unsafe { CStr::from_ptr(ptr) };
    Some(cstr.to_string_lossy().into_owned())
}

fn model_name_or_default(name_ptr: *const c_char) -> String {
    cstr_to_string(name_ptr)
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| DEFAULT_MODEL.to_string())
}

// --- Named model helpers ---

fn load_model_impl(name: &str, json: &str) -> bool {
    let Some(model) = RandomForestRunner::from_json(json) else {
        log_debug(&format!("load_model({name}): RandomForestRunner::from_json failed"));
        return false;
    };
    if let Ok(mut guard) = state().lock() {
        guard.models.insert(name.to_string(), model);
        log_debug(&format!("load_model({name}): loaded"));
        return true;
    }
    false
}

fn get_class_ids_impl(name: &str) -> Option<Vec<i32>> {
    let guard = state().lock().ok()?;
    let ids = guard.models.get(name)?.class_ids().to_vec();
    Some(ids)
}

fn predict_impl(name: &str, feature_rows: &[Vec<f32>]) -> Option<Vec<Vec<f32>>> {
    let guard = state().lock().ok()?;
    guard.models.get(name)?.predict_probabilities(feature_rows)
}

fn unload_model_impl(name: &str) {
    if let Ok(mut guard) = state().lock() {
        guard.models.remove(name);
        log_debug(&format!("unload_model({name}): removed"));
    }
}

// --- Backward-compatible default-model functions ---

#[no_mangle]
pub extern "C" fn rf_load_model(model_json: *const c_char) -> c_int {
    let Some(json) = cstr_to_string(model_json) else {
        log_debug("rf_load_model(): failed to decode input json");
        return 0;
    };
    if load_model_impl(DEFAULT_MODEL, &json) { 1 } else { 0 }
}

#[no_mangle]
pub extern "C" fn rf_get_class_ids(out_len: *mut c_int) -> *mut c_int {
    let ids = match get_class_ids_impl(DEFAULT_MODEL) {
        Some(v) => v,
        None => {
            log_debug("rf_get_class_ids(): no model loaded");
            if !out_len.is_null() { unsafe { *out_len = 0; } }
            return std::ptr::null_mut();
        }
    };
    let len = ids.len();
    let mut boxed = ids.into_boxed_slice();
    let ptr = boxed.as_mut_ptr();
    std::mem::forget(boxed);
    if !out_len.is_null() { unsafe { *out_len = len as c_int; } }
    log_debug(&format!("rf_get_class_ids(): returning {} ids", len));
    ptr
}

#[no_mangle]
pub extern "C" fn rf_predict_probabilities(
    data: *const c_double,
    rows: c_int,
    cols: c_int,
    out_rows: *mut c_int,
    out_cols: *mut c_int,
) -> *mut c_double {
    predict_probabilities_impl(DEFAULT_MODEL, data, rows, cols, out_rows, out_cols)
}

#[no_mangle]
pub extern "C" fn rf_unload_model() {
    unload_model_impl(DEFAULT_MODEL);
}

// --- Named model functions ---

#[no_mangle]
pub extern "C" fn rf_load_model_named(model_name: *const c_char, model_json: *const c_char) -> c_int {
    let name = model_name_or_default(model_name);
    let Some(json) = cstr_to_string(model_json) else {
        log_debug(&format!("rf_load_model_named({name}): failed to decode input json"));
        return 0;
    };
    if load_model_impl(&name, &json) { 1 } else { 0 }
}

#[no_mangle]
pub extern "C" fn rf_get_class_ids_named(model_name: *const c_char, out_len: *mut c_int) -> *mut c_int {
    let name = model_name_or_default(model_name);
    let ids = match get_class_ids_impl(&name) {
        Some(v) => v,
        None => {
            log_debug(&format!("rf_get_class_ids_named({name}): no model loaded"));
            if !out_len.is_null() { unsafe { *out_len = 0; } }
            return std::ptr::null_mut();
        }
    };
    let len = ids.len();
    let mut boxed = ids.into_boxed_slice();
    let ptr = boxed.as_mut_ptr();
    std::mem::forget(boxed);
    if !out_len.is_null() { unsafe { *out_len = len as c_int; } }
    log_debug(&format!("rf_get_class_ids_named({name}): returning {} ids", len));
    ptr
}

#[no_mangle]
pub extern "C" fn rf_predict_probabilities_named(
    model_name: *const c_char,
    data: *const c_double,
    rows: c_int,
    cols: c_int,
    out_rows: *mut c_int,
    out_cols: *mut c_int,
) -> *mut c_double {
    let name = model_name_or_default(model_name);
    predict_probabilities_impl(&name, data, rows, cols, out_rows, out_cols)
}

fn predict_probabilities_impl(
    name: &str,
    data: *const c_double,
    rows: c_int,
    cols: c_int,
    out_rows: *mut c_int,
    out_cols: *mut c_int,
) -> *mut c_double {
    log_debug(&format!("predict({name}): rows={rows} cols={cols}"));
    if data.is_null() || rows <= 0 || cols <= 0 {
        log_debug("predict: invalid input");
        if !out_rows.is_null() { unsafe { *out_rows = 0; } }
        if !out_cols.is_null() { unsafe { *out_cols = 0; } }
        return std::ptr::null_mut();
    }

    let rows = rows as usize;
    let cols = cols as usize;
    let slice = unsafe { std::slice::from_raw_parts(data, rows * cols) };

    let mut feature_rows: Vec<Vec<f32>> = Vec::with_capacity(rows);
    for r in 0..rows {
        let mut row = Vec::with_capacity(cols);
        for c in 0..cols {
            row.push(slice[r * cols + c] as f32);
        }
        feature_rows.push(row);
    }

    match predict_impl(name, &feature_rows) {
        Some(probs) if !probs.is_empty() => {
            let n_classes = probs[0].len();
            let total = probs.len() * n_classes;
            let mut flat = Vec::with_capacity(total);
            for p in &probs {
                for &v in p {
                    flat.push(v as f64);
                }
            }
            let mut boxed = flat.into_boxed_slice();
            let ptr = boxed.as_mut_ptr();
            std::mem::forget(boxed);
            if !out_rows.is_null() { unsafe { *out_rows = probs.len() as c_int; } }
            if !out_cols.is_null() { unsafe { *out_cols = n_classes as c_int; } }
            log_debug(&format!("predict({name}): returning {}x{}", probs.len(), n_classes));
            ptr
        }
        Some(_) => {
            log_debug(&format!("predict({name}): empty results"));
            if !out_rows.is_null() { unsafe { *out_rows = 0; } }
            if !out_cols.is_null() { unsafe { *out_cols = 0; } }
            std::ptr::null_mut()
        }
        None => {
            log_debug(&format!("predict({name}): prediction failed"));
            if !out_rows.is_null() { unsafe { *out_rows = 0; } }
            if !out_cols.is_null() { unsafe { *out_cols = 0; } }
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "C" fn rf_unload_model_named(model_name: *const c_char) {
    let name = model_name_or_default(model_name);
    unload_model_impl(&name);
}

#[no_mangle]
pub extern "C" fn rf_free_data(ptr: *mut c_double) {
    if !ptr.is_null() {
        unsafe {
            let _ = Box::from_raw(std::slice::from_raw_parts_mut(ptr, 0));
        }
    }
}

#[no_mangle]
pub extern "C" fn rf_free_ints(ptr: *mut c_int) {
    if !ptr.is_null() {
        unsafe {
            let _ = Box::from_raw(std::slice::from_raw_parts_mut(ptr, 0));
        }
    }
}
