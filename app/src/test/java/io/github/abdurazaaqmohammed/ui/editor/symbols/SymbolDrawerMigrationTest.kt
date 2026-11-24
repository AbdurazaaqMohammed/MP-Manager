package io.github.abdurazaaqmohammed.ui.editor.symbols

import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolEntry
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolDrawerMigrationTest {

    private fun entry(id: Long, label: String) = SymbolEntry(
        id = id,
        label = label,
        shortAction = SymbolAction.InsertText(label)
    )

    @Test
    fun ensureDefaultGroups_prependsMissingCustomAndSymbolsOnLeft() {
        val imported = SymbolGroup("mt_imported", "MT Group", listOf(entry(500L, "const")))

        val result = SymbolDrawerMigration.ensureDefaultGroups(listOf(imported))

        assertEquals(3, result.size)
        assertEquals("custom", result[0].id)
        assertEquals("symbols", result[1].id)
        assertEquals("mt_imported", result[2].id)
    }

    @Test
    fun ensureDefaultGroups_doesNotDuplicateWhenMatchedByIdEvenIfRenamed() {
        val renamedCustom = SymbolGroup("custom", "مخصص", listOf(entry(10L, "Copy")))
        val renamedSymbols = SymbolGroup("symbols", "رموز", listOf(entry(11L, "{")))
        val original = listOf(renamedCustom, renamedSymbols)

        val result = SymbolDrawerMigration.ensureDefaultGroups(original)

        assertSame(original, result)
    }

    @Test
    fun ensureDefaultGroups_doesNotDuplicateWhenMatchedByLocalizedTitle() {
        val localizedCustom = SymbolGroup("legacy_1", "مخصص", listOf(entry(10L, "Copy")))
        val localizedSymbols = SymbolGroup("legacy_2", "رموز", listOf(entry(11L, "{")))
        val original = listOf(localizedCustom, localizedSymbols)

        val result = SymbolDrawerMigration.ensureDefaultGroups(
            currentGroups = original,
            createCustomGroup = { SymbolGroup("custom", "مخصص", listOf(entry(1001L, "Search"))) },
            createSymbolsGroup = { SymbolGroup("symbols", "رموز", listOf(entry(1005L, "("))) }
        )

        assertSame(original, result)
    }

    @Test
    fun ensureDefaultGroups_reassignsCollidingAndZeroSymbolIdsWithoutMutatingExisting() {
        val existing = SymbolGroup("user", "User", listOf(entry(1L, "A"), entry(1001L, "B")))

        val result = SymbolDrawerMigration.ensureDefaultGroups(
            currentGroups = listOf(existing),
            createCustomGroup = { SymbolGroup("custom", "Custom", listOf(entry(0L, "Z"), entry(1001L, "Search"))) },
            createSymbolsGroup = { SymbolGroup("symbols", "Symbols", listOf(entry(1005L, "("))) }
        )

        val allIds = result.flatMap { it.symbols }.map { it.id }
        assertEquals(allIds.size, allIds.toSet().size)
        assertTrue(allIds.none { it == 0L })
        assertEquals(listOf(1L, 1001L), result.last().symbols.map { it.id })
    }
}
