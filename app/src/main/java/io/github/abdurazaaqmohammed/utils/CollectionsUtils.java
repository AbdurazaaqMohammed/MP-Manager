package io.github.abdurazaaqmohammed.utils;

import java.util.List;

import org.apache.commons.collections4.Predicate;

public class CollectionsUtils {
    public static boolean removeIf(List list, Predicate filter) {
        boolean removed = false;
        for (int i = 0, listSize = list.size(); i < listSize; i++) {
            Object item = list.get(i);
            if (filter.evaluate(item)) {
                list.remove(item);
                removed = true;
            }
        }
        return removed;
    }

}
