package ru.lebalexvla.slotbookingbot.category

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.common.ListPage
import ru.lebalexvla.slotbookingbot.contact.ContactRepository
import ru.lebalexvla.slotbookingbot.user.UserRepository
import ru.lebalexvla.slotbookingbot.user.UserSummary
import ru.lebalexvla.slotbookingbot.user.toSummary
import java.time.Instant
import java.util.UUID

@Service
class CategoryService(
    private val categoryRepository: CategoryRepository,
    private val categoryMemberRepository: CategoryMemberRepository,
    private val contactRepository: ContactRepository,
    private val userRepository: UserRepository
) {
    @Transactional
    fun createCategory(ownerId: UUID, name: String): CategorySummary {
        val normalizedName = name.trim().replace(Regex("\\s+"), " ")
        if (normalizedName.length !in 1..64) throw BusinessException(BusinessError.INVALID_CATEGORY_NAME)
        val owner = userRepository.findLockedById(ownerId)
            ?: throw BusinessException(BusinessError.USER_NOT_FOUND)
        if (categoryRepository.existsByOwnerIdAndNameAndArchivedAtIsNull(ownerId, normalizedName)) {
            throw BusinessException(BusinessError.CATEGORY_NAME_TAKEN)
        }
        return categoryRepository.save(Category(owner = owner, name = normalizedName)).toSummary()
    }

    @Transactional
    fun addMember(ownerId: UUID, categoryId: UUID, userId: UUID) {
        val category = getLockedCategory(ownerId, categoryId)
        if (!contactRepository.existsByOwnerIdAndContactUserId(ownerId, userId)) {
            throw BusinessException(BusinessError.CONTACT_REQUIRED)
        }
        if (!categoryMemberRepository.existsByCategoryIdAndUserId(categoryId, userId)) {
            val user = userRepository.findById(userId)
                .orElseThrow { BusinessException(BusinessError.USER_NOT_FOUND) }
            categoryMemberRepository.save(CategoryMember(category = category, user = user))
        }
    }

    @Transactional
    fun removeMember(ownerId: UUID, categoryId: UUID, userId: UUID) {
        getLockedCategory(ownerId, categoryId)
        categoryMemberRepository.deleteByCategoryIdAndUserId(categoryId, userId)
    }

    @Transactional
    fun archiveCategory(ownerId: UUID, categoryId: UUID) {
        val category = categoryRepository.findLockedByIdAndOwnerId(categoryId, ownerId)
            ?: throw BusinessException(BusinessError.CATEGORY_NOT_FOUND)
        if (category.archivedAt == null) category.archivedAt = Instant.now()
    }

    @Transactional(readOnly = true)
    fun getCategories(ownerId: UUID, page: Int = 0): ListPage<CategorySummary> =
        ListPage.from(
            categoryRepository.findAllByOwnerIdAndArchivedAtIsNullOrderByCreatedAtAscIdAsc(
                ownerId, ListPage.request(page)
            )
        ) { it.toSummary() }

    @Transactional(readOnly = true)
    fun getCategory(ownerId: UUID, categoryId: UUID): CategorySummary =
        (categoryRepository.findByIdAndOwnerIdAndArchivedAtIsNull(categoryId, ownerId)
            ?: throw BusinessException(BusinessError.CATEGORY_NOT_FOUND)).toSummary()

    @Transactional(readOnly = true)
    fun getMembers(ownerId: UUID, categoryId: UUID, page: Int = 0): ListPage<UserSummary> {
        getCategory(ownerId, categoryId)
        return ListPage.from(
            categoryMemberRepository.findAllByCategoryIdOrderByAddedAtAscIdAsc(categoryId, ListPage.request(page))
        ) { it.user.toSummary() }
    }

    @Transactional(readOnly = true)
    fun getCandidates(ownerId: UUID, categoryId: UUID, page: Int = 0): ListPage<UserSummary> {
        getCategory(ownerId, categoryId)
        return ListPage.from(
            categoryMemberRepository.findCandidates(ownerId, categoryId, ListPage.request(page))
        ) { it.contactUser.toSummary() }
    }

    private fun getLockedCategory(ownerId: UUID, categoryId: UUID): Category =
        categoryRepository.findLockedByIdAndOwnerIdAndArchivedAtIsNull(categoryId, ownerId)
            ?: throw BusinessException(BusinessError.CATEGORY_NOT_FOUND)
}
