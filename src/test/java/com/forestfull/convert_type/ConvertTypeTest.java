package com.forestfull.convert_type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ConvertTypeTest {

    @Test
    @DisplayName("기본 객체 변환 테스트 (Entity -> DTO)")
    void testEntityToDtoConversion() {
        System.out.println("=== 기본 객체 변환 테스트 (Entity -> DTO) ===");
        // Given
        UserEntity entity = new UserEntity("user1", "password123", "John Doe", 30);
        System.out.println("Source: " + entity);

        // When
        UserDto dto = ConvertType.from(entity).to(UserDto.class);

        // Then
        assertThat(dto).isNotNull();
        System.out.println("Target: " + dto);
        assertThat(dto.username).isEqualTo("user1");
        assertThat(dto.fullName).isEqualTo("John Doe");
        assertThat(dto.age).isEqualTo(30);
        System.out.println("--------------------------------------------------\n");
    }

    @Test
    @DisplayName("중첩 객체 변환 테스트 (Order -> OrderDto)")
    void testNestedObjectConversion() {
        System.out.println("=== 중첩 객체 변환 테스트 (Order -> OrderDto) ===");
        // Given
        ProductEntity product = new ProductEntity("P001", "Laptop", 1500.0);
        OrderEntity order = new OrderEntity("ORD-001", product, 2);
        System.out.println("Source: " + order);

        // When
        OrderDto dto = ConvertType.from(order).to(OrderDto.class);

        // Then
        assertThat(dto).isNotNull();
        System.out.println("Target: " + dto);
        assertThat(dto.orderId).isEqualTo("ORD-001");
        assertThat(dto.product).isNotNull();
        assertThat(dto.product.productName).isEqualTo("Laptop");
        assertThat(dto.quantity).isEqualTo(2);
        System.out.println("--------------------------------------------------\n");
    }

    @Test
    @DisplayName("컬렉션 포함 객체 변환 테스트 (List<Entity> -> List<Dto>)")
    void testCollectionConversion() {
        System.out.println("=== 컬렉션 포함 객체 변환 테스트 (List<Entity> -> List<Dto>) ===");
        // Given
        ProductEntity p1 = new ProductEntity("P001", "Mouse", 20.0);
        ProductEntity p2 = new ProductEntity("P002", "Keyboard", 50.0);
        
        CategoryEntity category = new CategoryEntity("Electronics", Arrays.asList(p1, p2));
        System.out.println("Source: " + category);

        // When
        CategoryDto dto = ConvertType.from(category).to(CategoryDto.class);

        // Then
        assertThat(dto).isNotNull();
        System.out.println("Target: " + dto);
        assertThat(dto.name).isEqualTo("Electronics");
        assertThat(dto.products).hasSize(2);
        assertThat(dto.products.get(0).productName).isEqualTo("Mouse");
        assertThat(dto.products.get(1).productName).isEqualTo("Keyboard");
        assertThat(dto.products).isInstanceOf(ArrayList.class);
        System.out.println("--------------------------------------------------\n");
    }

    @Test
    @DisplayName("복합 타입 변환 테스트 (Map, List, Primitive 혼합)")
    void testComplexTypeConversion() {
        System.out.println("=== 복합 타입 변환 테스트 (Map, List, Primitive 혼합) ===");
        // Given
        ComplexSource source = new ComplexSource();
        source.id = 100L;
        source.tags = Arrays.asList("tag1", "tag2");
        source.metadata = new HashMap<>();
        source.metadata.put("created", "2023-01-01");
        source.metadata.put("author", "admin");
        source.isActive = true;
        System.out.println("Source: " + source);

        // When
        ComplexTarget target = ConvertType.from(source).to(ComplexTarget.class);

        // Then
        assertThat(target).isNotNull();
        System.out.println("Target: " + target);
        assertThat(target.id).isEqualTo(100L);
        assertThat(target.tags).containsExactly("tag1", "tag2");
        assertThat(target.metadata).containsEntry("created", "2023-01-01");
        assertThat(target.isActive).isTrue();
        System.out.println("--------------------------------------------------\n");
    }

    @Test
    @DisplayName("@ConvertField mapping 테스트")
    void testmappingAnnotation() {
        System.out.println("=== @ConvertField mapping 테스트 ===");
        // Given
        UserEntity entity = new UserEntity("user_mappingd", "pw", "mappingd User", 25);
        System.out.println("Source: " + entity);

        // When
        UserDtomappingd target = ConvertType.from(entity).to(UserDtomappingd.class);

        // Then
        assertThat(target).isNotNull();
        System.out.println("Target: " + target);
        assertThat(target.loginId).isEqualTo("user_mappingd"); // username -> loginId
        System.out.println("--------------------------------------------------\n");
    }

    @Test
    @DisplayName("@ConvertField ignore 테스트")
    void testIgnoreAnnotation() {
        System.out.println("=== @ConvertField ignore 테스트 ===");
        // Given
        UserEntity entity = new UserEntity("ignore_user", "secret", "Ignore Me", 99);
        System.out.println("Source: " + entity);

        // When
        UserDtoIgnored target = ConvertType.from(entity).to(UserDtoIgnored.class);

        // Then
        assertThat(target).isNotNull();
        System.out.println("Target: " + target);
        assertThat(target.username).isNull(); // ignore
        assertThat(target.fullName).isEqualTo("Ignore Me");
        System.out.println("--------------------------------------------------\n");
    }

    // --- Test Classes ---

    static class UserEntity {
        String username;
        String password;
        String fullName;
        int age;

        public UserEntity(String username, String password, String fullName, int age) {
            this.username = username;
            this.password = password;
            this.fullName = fullName;
            this.age = age;
        }

        @Override
        public String toString() {
            return "UserEntity{username='" + username + "', password='***', fullName='" + fullName + "', age=" + age + "}";
        }
    }

    static class UserDto {
        String username;
        String fullName;
        int age;

        @Override
        public String toString() {
            return "UserDto{username='" + username + "', fullName='" + fullName + "', age=" + age + "}";
        }
    }

    static class ProductEntity {
        String productId;
        String productName;
        double price;

        public ProductEntity(String productId, String productName, double price) {
            this.productId = productId;
            this.productName = productName;
            this.price = price;
        }

        @Override
        public String toString() {
            return "ProductEntity{productId='" + productId + "', productName='" + productName + "', price=" + price + "}";
        }
    }

    static class ProductDto {
        String productName;
        double price;

        @Override
        public String toString() {
            return "ProductDto{productName='" + productName + "', price=" + price + "}";
        }
    }

    static class OrderEntity {
        String orderId;
        ProductEntity product;
        int quantity;

        public OrderEntity(String orderId, ProductEntity product, int quantity) {
            this.orderId = orderId;
            this.product = product;
            this.quantity = quantity;
        }

        @Override
        public String toString() {
            return "OrderEntity{orderId='" + orderId + "', product=" + product + ", quantity=" + quantity + "}";
        }
    }

    static class OrderDto {
        String orderId;
        ProductDto product;
        int quantity;

        @Override
        public String toString() {
            return "OrderDto{orderId='" + orderId + "', product=" + product + ", quantity=" + quantity + "}";
        }
    }

    static class CategoryEntity {
        String name;
        List<ProductEntity> products;

        public CategoryEntity(String name, List<ProductEntity> products) {
            this.name = name;
            this.products = products;
        }

        @Override
        public String toString() {
            return "CategoryEntity{name='" + name + "', products=" + products + "}";
        }
    }

    static class CategoryDto {
        String name;
        List<ProductDto> products;

        @Override
        public String toString() {
            return "CategoryDto{name='" + name + "', products=" + products + "}";
        }
    }

    static class ComplexSource {
        Long id;
        List<String> tags;
        Map<String, String> metadata;
        boolean isActive;

        @Override
        public String toString() {
            return "ComplexSource{id=" + id + ", tags=" + tags + ", metadata=" + metadata + ", isActive=" + isActive + "}";
        }
    }

    static class ComplexTarget {
        Long id;
        List<String> tags;
        Map<String, String> metadata;
        boolean isActive;

        @Override
        public String toString() {
            return "ComplexTarget{id=" + id + ", tags=" + tags + ", metadata=" + metadata + ", isActive=" + isActive + "}";
        }
    }

    static class UserDtomappingd {
        @ConvertField(mapping = "username")
        String loginId;
        String fullName;

        @Override
        public String toString() {
            return "UserDtomappingd{loginId='" + loginId + "', fullName='" + fullName + "'}";
        }
    }

    static class UserDtoIgnored {
        @ConvertField(ignore = true)
        String username;
        String fullName;

        @Override
        public String toString() {
            return "UserDtoIgnored{username='" + username + "', fullName='" + fullName + "'}";
        }
    }
}
