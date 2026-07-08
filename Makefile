# Lệnh check lỗi code toàn bộ dự án Android
.PHONY: lint
lint:
	./gradlew lint

# Lệnh tự động sửa các lỗi format dấu cách/xuống dòng của Kotlin
.PHONY: format
format:
	./gradlew ktlintFormat