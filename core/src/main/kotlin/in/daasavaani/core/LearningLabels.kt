package `in`.daasavaani.core

data class LearningAssignment(val reference: PersonalReference, val label: String)

object LearningLabels {
    val labels = listOf("wantToLearn", "practising", "comfortableReciting")

    fun valid(entries: List<LearningAssignment>): Boolean {
        if (entries.size > PersonalPolicy.maxLearningLabels) return false
        val identities = HashSet<PersonalReference>(entries.size)
        return entries.all { it.reference.valid() && it.label in labels && identities.add(it.reference) }
    }

    fun change(entries: List<LearningAssignment>, reference: PersonalReference, label: String?): List<LearningAssignment> {
        require(valid(entries) && reference.valid() && (label == null || label in labels))
        val index = entries.indexOfFirst { it.reference == reference }
        if (index < 0 && label == null || index >= 0 && entries[index].label == label) return entries
        if (label == null) return entries.filterNot { it.reference == reference }
        if (index < 0) {
            require(entries.size < PersonalPolicy.maxLearningLabels)
            return entries + LearningAssignment(reference, label)
        }
        return entries.toMutableList().apply { this[index] = LearningAssignment(reference, label) }
    }

    fun filtered(entries: List<LearningAssignment>, label: String?): List<LearningAssignment> {
        require(label == null || label in labels)
        return if (label == null) entries else entries.filter { it.label == label }
    }
}
