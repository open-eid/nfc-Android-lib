/*
 * Test-classpath shim for android.util.SparseArray.
 *
 * The Android SDK's android.jar provides a stub of SparseArray that
 * throws "Method not mocked" at runtime under plain JVM unit tests.
 * Replaying real-card sessions through {@code Idemia#personalData}
 * (which uses {@code new SparseArray<>()}) needs a working
 * implementation. Robolectric would do this but is a heavy dependency
 * for one test class, so this minimal HashMap-backed shim sits on the
 * test source root and shadows the SDK stub when the lib code is loaded
 * by the test classloader.
 *
 * <p>Production callers never see this — the real Android runtime
 * provides the actual SparseArray.
 */
package android.util;

import java.util.HashMap;
import java.util.Map;

public class SparseArray<E> {
    private final Map<Integer, E> map = new HashMap<>();

    public SparseArray() {
    }

    public SparseArray(int initialCapacity) {
    }

    public void put(int key, E value) {
        map.put(key, value);
    }

    public E get(int key) {
        return map.get(key);
    }

    public E get(int key, E valueIfKeyNotFound) {
        E v = map.get(key);
        return v != null ? v : valueIfKeyNotFound;
    }

    public void set(int key, E value) {
        put(key, value);
    }

    public int size() {
        return map.size();
    }

    public void remove(int key) {
        map.remove(key);
    }

    public void clear() {
        map.clear();
    }
}
