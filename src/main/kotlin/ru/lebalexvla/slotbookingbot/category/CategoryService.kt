package ru.lebalexvla.slotbookingbot.category

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.lebalexvla.slotbookingbot.contact.ContactRepository
import ru.lebalexvla.slotbookingbot.user.UserRepository
import java.util.UUID

@Service
class CategoryService(
    private val categoryRepository: CategoryRepository,
    private val categoryMemberRepository: CategoryMemberRepository,
    private val contactRepository: ContactRepository,
    private val userRepository: UserRepository
) {

    @Transactional
    fun createCategory(
        ownerId: UUID,
        name: String
    ): Category {
        val normalizedName = name.trim()

        require(normalizedName.isNotEmpty()) {
            "Category name cannot be empty"
        }

        require(
            !categoryRepository.existsByOwnerIdAndName(
                ownerId,
                normalizedName
            )
        ) {
            "Category already exists"
        }

        val owner = userRepository.findById(ownerId)
            .orElseThrow {
                IllegalArgumentException("Owner not found")
            }

        return categoryRepository.save(
            Category(
                owner = owner,
                name = normalizedName
            )
        )
    }

    @Transactional
    fun addMember(
        ownerId: UUID,
        categoryId: UUID,
        userId: UUID
    ) {
        val category = categoryRepository.findByIdAndOwnerId(
            categoryId,
            ownerId
        ) ?: throw IllegalArgumentException("Category not found")

        require(
            contactRepository.existsByOwnerIdAndContactUserId(
                ownerId,
                userId
            )
        ) {
            "User is not a contact"
        }

        if (
            categoryMemberRepository.existsByCategoryIdAndUserId(
                categoryId,
                userId
            )
        ) {
            return
        }

        val user = userRepository.findById(userId)
            .orElseThrow {
                IllegalArgumentException("User not found")
            }

        categoryMemberRepository.save(
            CategoryMember(
                category = category,
                user = user
            )
        )
    }

    @Transactional(readOnly = true)
    fun getCategories(ownerId: UUID): List<Category> =
        categoryRepository.findAllByOwnerId(ownerId)

    @Transactional(readOnly = true)
    fun getMembers(categoryId: UUID): List<CategoryMember> =
        categoryMemberRepository.findAllByCategoryId(categoryId)
}