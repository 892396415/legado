# 多模块 Gradle 配置 - 详细学习计划

> 基于 Legado 项目学习 Android 多模块 Gradle 配置

---

## 一、概述

### 1.1 什么是多模块 Gradle 配置

多模块 Gradle 配置是将一个大型项目拆分为多个独立的子模块（Module），每个模块可以独立编译、测试和复用。在 Android 项目中，常见的模块类型包括：

- **app 模块**：主应用程序模块
- **library 模块**：功能库模块
- **dynamic-feature 模块**：动态功能模块

### 1.2 Legado 项目的模块结构

Legado 项目采用了多模块结构：

```
legado/
├── app/                    # 主应用模块
├── modules/
│   ├── book/               # 书籍解析模块（epublib/umdlib）
│   └── rhino/             # JavaScript 脚本引擎模块
├── build.gradle            # 根项目构建配置
├── settings.gradle         # 模块配置
└── gradle/
    └── libs.versions.toml  # 依赖版本管理
```

---

## 二、关键配置文件

### 2.1 settings.gradle - 模块注册

**文件位置**：`settings.gradle`

```groovy
// 插件仓库配置
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

// 依赖仓库配置
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url 'https://jitpack.io' }
        mavenCentral()
    }
}

rootProject.name = 'legado'

// 注册子模块
include ':app'
include ':modules:book'
include ':modules:rhino'
```

**学习要点**：
- `include` 声明模块路径
- `dependencyResolutionManagement` 统一管理依赖仓库
- `RepositoriesMode.FAIL_ON_PROJECT_REPOS` 强制所有模块使用统一的仓库配置

---

### 2.2 build.gradle - 根项目配置

**文件位置**：`build.gradle`

```groovy
// 根项目配置
buildscript {
    ext {
        compile_sdk_version = 35
        build_tool_version = '34.0.0'
    }
}

// 插件声明
plugins {
    alias libs.plugins.android.application apply false
    alias libs.plugins.android.library apply false
    alias libs.plugins.kotlin.android apply false
    alias libs.plugins.ksp apply false
    alias libs.plugins.room apply false
}

tasks.register('clean', Delete) {
    delete rootProject.layout.buildDirectory
}
```

**学习要点**：
- `ext` 定义全局扩展属性
- `plugins` 声明项目级插件
- `apply false` 表示插件仅在根项目声明，不自动应用到子模块

---

### 2.3 libs.versions.toml - 版本目录

**文件位置**：`gradle/libs.versions.toml`

这是 Gradle 7.0+ 推荐的版本管理方式，类似 Kotlin 的版本目录：

```toml
[versions]
kotlin = "2.1.21"
ksp = "2.1.21-2.0.1"
agp = "8.9.1"
room = "2.7.1"
coroutines = "1.10.2"

[libraries]
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }

[bundles]
coroutines = ["kotlinx-coroutines-core", "kotlinx-coroutines-android"]

[plugins]
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
room = { id = "androidx.room", version.ref = "room" }
```

**学习要点**：
- `[versions]` 定义版本号
- `[libraries]` 定义依赖库
- `[bundles]` 定义依赖组
- `[plugins]` 定义插件

---

## 三、各模块配置详解

### 3.1 app 模块 - 主应用

**文件位置**：`app/build.gradle`

```groovy
plugins {
    alias libs.plugins.android.application
    alias libs.plugins.kotlin.android
    alias libs.plugins.kotlin.parcelize
    alias libs.plugins.room
    alias libs.plugins.ksp
    alias libs.plugins.google.services
}

android {
    compileSdk = compile_sdk_version
    namespace 'io.legado.app'
    
    kotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
    
    defaultConfig {
        applicationId "io.legado.app"
        minSdk 21
        targetSdk 35
        versionCode 10000
    }
    
    buildFeatures {
        buildConfig true
        viewBinding true
    }
}

dependencies {
    // 使用 toml 中定义的依赖
    implementation(libs.kotlin.stdlib)
    implementation(libs.bundles.coroutines)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    
    // 模块间依赖
    implementation(project(path: ':modules:book'))
    implementation(project(path: ':modules:rhino'))
}
```

---

### 3.2 modules/book 模块 - 书籍解析库

**文件位置**：`modules/book/build.gradle`

```groovy
plugins {
    alias libs.plugins.android.library
    alias libs.plugins.kotlin.android
}

android {
    compileSdk = compile_sdk_version
    namespace 'me.ag2s'
    
    defaultConfig {
        minSdk 21
        targetSdk 35
    }
}

dependencies {
    implementation(libs.androidx.annotation)
}
```

**模块说明**：
- 这是一个 **library 模块**
- 封装了 epub、umd 书籍解析功能
- 提供给主 app 模块使用

---

### 3.3 modules/rhino 模块 - 脚本引擎

**文件位置**：`modules/rhino/build.gradle`

```groovy
plugins {
    alias libs.plugins.android.library
    alias libs.plugins.kotlin.android
}

android {
    compileSdk = compile_sdk_version
    namespace 'com.script'
    
    defaultConfig {
        minSdk 21
        targetSdk 35
    }
}

dependencies {
    api libs.mozilla.rhino
    
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
}
```

**模块说明**：
- 封装了 Mozilla Rhino JavaScript 引擎
- 用于执行书源规则脚本
- 使用 `api` 而非 `implementation`，让依赖方也能使用

---

## 四、模块间依赖管理

### 4.1 依赖配置区别

| 配置 | 可见性 | 传递性 |
|------|--------|--------|
| `implementation` | 当前模块 | 不传递 |
| `api` | 当前模块 | 传递（类似旧版 `compile`） |
| `compileOnly` | 仅编译时 | 不传递 |
| `runtimeOnly` | 仅运行时 | 不传递 |

**示例**：

```groovy
// modules/rhino/build.gradle
dependencies {
    // 公开给依赖方
    api libs.mozilla.rhino
    
    // 仅内部使用
    implementation(libs.kotlinx.coroutines.core)
}

// app/build.gradle
dependencies {
    // 引入 rhino 模块
    implementation(project(path: ':modules:rhino'))
    // rhino 模块的 rhino 库可以直接使用
}
```

---

### 4.2 版本统一管理

**优点**：
1. 单一版本来源，避免版本冲突
2. 升级版本只需修改一处
3. 依赖关系清晰

**示例**：

```groovy
// 在 toml 中定义
[versions]
okhttp = "4.12.0"

[libraries]
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }

// 在模块中使用
dependencies {
    implementation(libs.okhttp)
}
```

---

## 五、学习路径

### 阶段一：理解配置文件结构（1天）

#### 目标
理解各个配置文件的作用和关系

#### 任务

1. **阅读 settings.gradle**
   - 理解模块注册方式
   - 理解仓库配置

2. **阅读 build.gradle（根项目）**
   - 理解插件声明方式
   - 理解 ext 全局属性

3. **阅读 libs.versions.toml**
   - 理解版本目录格式
   - 理解 libraries、bundles、plugins 定义

#### 验证
能回答以下问题：
- 项目有几个模块？
- 模块间依赖如何配置？
- 依赖版本在哪里统一管理？

---

### 阶段二：分析 app 模块配置（1-2天）

#### 目标
掌握 app 模块的完整配置

#### 任务

1. **阅读 app/build.gradle**
   - 分析插件配置
   - 分析 android {} 配置块
   - 分析 dependencies {}

2. **理解关键配置项**
   - `namespace`：包名
   - `minSdk` / `targetSdk` / `compileSdk`
   - `jvmToolchain`：Java 版本
   - `buildFeatures`：构建特性

3. **理解 ViewBinding 和 Room 配置**
   ```groovy
   buildFeatures {
       viewBinding true
       buildConfig true
   }
   
   room {
       schemaDirectory "$projectDir/schemas"
   }
   ```

#### 实践任务
尝试修改 app 模块的 minSdk 版本，观察变化

---

### 阶段三：分析子模块配置（1-2天）

#### 目标
理解 library 模块与 app 模块的区别

#### 任务

1. **对比 app 与 book 模块**
   - 插件差异（application vs library）
   - namespace 差异
   - 依赖配置差异

2. **理解 rhino 模块**
   - api 与 implementation 的区别
   - 脚本引擎的使用方式

#### 验证
能回答：
- library 模块与 app 模块的核心区别是什么？
- api 和 implementation 有什么区别？

---

### 阶段四：模块间依赖实践（1-2天）

#### 目标
掌握模块间依赖的配置方法

#### 任务

1. **理解依赖引用方式**
   ```groovy
   // 引用本地模块
   implementation(project(path: ':modules:rhino'))
   
   // 引用 toml 中定义的库
   implementation(libs.okhttp)
   ```

2. **理解依赖传递性**
   - 使用 `api` 公开依赖
   - 使用 `implementation` 隐藏依赖

#### 实践任务
在 app 模块中添加一个新依赖，使用 toml 统一管理版本

---

### 阶段五：自定义模块实践（2-3天）

#### 目标
能够创建和配置新的功能模块

#### 任务

1. **创建新模块步骤**
   ```bash
   # 1. 在 settings.gradle 中添加
   include ':modules:newmodule'
   
   # 2. 创建模块目录结构
   modules/newmodule/
   ├── build.gradle
   ├── src/main/
   │   ├── java/...
   │   └── AndroidManifest.xml
   ```

2. **配置模块 build.gradle**
   ```groovy
   plugins {
       alias libs.plugins.android.library
       alias libs.plugins.kotlin.android
   }
   
   android {
       namespace 'com.example.newmodule'
       compileSdk = compile_sdk_version
   }
   
   dependencies {
       implementation(libs.kotlin.stdlib)
   }
   ```

3. **在 app 中引用**
   ```groovy
   implementation(project(path: ':modules:newmodule'))
   ```

#### 验证
成功创建一个新模块并被 app 模块引用

---

## 六、常见问题

### Q1: 如何统一管理依赖版本？

使用 `gradle/libs.versions.toml` 文件：

```toml
[versions]
okhttp = "4.12.0"

[libraries]
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
```

然后在模块中使用：
```groovy
implementation(libs.okhttp)
```

### Q2: 模块间如何共享依赖？

在子模块中使用 `api` 配置：

```groovy
// modules/rhino/build.gradle
dependencies {
    api libs.mozilla.rhino  // 传递依赖
}
```

### Q3: 如何处理依赖冲突？

1. 使用 `constraints` 强制版本：
   ```groovy
   configurations.all {
       resolutionStrategy {
           force 'com.squareup.okhttp3:okhttp:4.12.0'
       }
   }
   ```

2. 在 toml 中统一定义版本

### Q4: 多模块项目的优势？

| 优势 | 说明 |
|------|------|
| 模块化 | 功能解耦，易于维护 |
| 复用性 | 公共代码抽取为独立模块 |
| 并行构建 | 提升编译速度 |
| 独立测试 | 每个模块可单独测试 |

---

## 七、关键概念总结

### 7.1 Gradle 文件结构

```
项目根目录/
├── build.gradle              # 根项目构建脚本
├── settings.gradle           # 项目设置（模块注册）
├── gradle.properties         # Gradle 属性
├── gradle/
│   └── libs.versions.toml    # 版本目录（依赖管理）
├── app/                      # 应用模块
│   └── build.gradle
└── modules/                  # 功能模块目录
    ├── book/
    │   └── build.gradle
    └── rhino/
        └── build.gradle
```

### 7.2 插件类型

| 插件 | 用途 |
|------|------|
| `com.android.application` | Android 应用 |
| `com.android.library` | Android 库 |
| `org.jetbrains.kotlin.android` | Kotlin Android 支持 |
| `androidx.room` | Room 数据库 |
| `com.google.devtools.ksp` | KSP 注解处理器 |

### 7.3 依赖配置对比

| 配置 | 编译时可见 | 运行时可见 | 传递性 |
|------|-----------|-----------|--------|
| `implementation` | ✅ | ✅ | ❌ |
| `api` | ✅ | ✅ | ✅ |
| `compileOnly` | ✅ | ❌ | ❌ |
| `runtimeOnly` | ❌ | ✅ | ❌ |

---

## 八、自测题目答案

### 题目 1：Legado 项目有几个模块？分别是什么？

**答案：**

Legado 项目目前有 **3 个模块**：

1. **app 模块** - 主应用程序模块
   - 路径：项目根目录下的 `app/`
   - 类型：`com.android.application`（应用模块）
   - 作用：包含所有的 UI 界面、业务逻辑、数据层代码，是用户直接交互的应用入口

2. **modules/book 模块** - 书籍解析模块
   - 路径：`modules/book/`
   - 类型：`com.android.library`（库模块）
   - 作用：封装了 epub、umd 等书籍格式的解析功能，基于 `me.ag2s` 包名
   - 包含：epublib（EPUB 解析）、umdlib（UMD 解析）等

3. **modules/rhino 模块** - JavaScript 脚本引擎模块
   - 路径：`modules/rhino/`
   - 类型：`com.android.library`（库模块）
   - 作用：封装了 Mozilla Rhino JavaScript 引擎，用于执行书源规则脚本
   - 包名：`com.script`

**模块依赖关系：**

```
app 模块
├── 依赖 modules:book（implementation）
└── 依赖 modules:rhino（implementation）
```

---

### 题目 2：`settings.gradle` 的作用是什么？

**答案：**

`settings.gradle` 是 Gradle 项目的核心配置文件，主要作用包括：

**1. 模块注册与管理**

```groovy
include ':app'
include ':modules:book'
include ':modules:rhino'
```

这行代码告诉 Gradle 项目中存在哪些子模块需要构建。没有在这里注册的模块，Gradle 不会进行构建。

**2. 项目名称定义**

```groovy
rootProject.name = 'legado'
```

定义根项目的名称，这个名称会出现在 Gradle 任务列表中。

**3. 插件仓库配置（pluginManagement）**

```groovy
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
```

配置插件下载的仓库顺序。Gradle 会按照这里列出的顺序依次查找插件。

**4. 依赖仓库配置（dependencyResolutionManagement）**

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url 'https://jitpack.io' }
        mavenCentral()
    }
}
```

- `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)`：强制所有子模块使用这里统一的仓库配置，不允许子模块单独定义仓库
- 配置了 Google、Maven Central、JitPack 等常用仓库

**5. 特性配置**

可以在 settings.gradle 中配置 Gradle 的各种特性，如缓存、并行构建等。

---

### 题目 3：`libs.versions.toml` 文件的优势是什么？

**答案：**

`libs.versions.toml` 是 Gradle 7.0 引入的版本目录（Version Catalog）功能，相比传统的依赖管理方式具有以下优势：

**1. 单一版本来源**

```toml
[versions]
kotlin = "2.1.21"
okhttp = "4.12.0"
room = "2.7.1"
```

所有依赖的版本都集中在一个文件中，升级版本只需修改这一处，避免了多处定义版本导致的不一致问题。

**2. 类型安全的依赖引用**

```groovy
// 传统方式（容易拼写错误）
implementation 'com.squareup.okhttp3:okhttp:4.12.0'

// toml 方式（IDE 自动补全）
implementation(libs.okhttp)
```

使用 `libs.xxx` 的方式引用依赖，IDE 可以提供自动补全和重构支持，大大减少拼写错误。

**3. 依赖分组管理**

```toml
[bundles]
coroutines = ["kotlinx-coroutines-core", "kotlinx-coroutines-android"]
```

可以将经常一起使用的多个依赖打包成组，一次性引入：

```groovy
implementation(libs.bundles.coroutines)
```

**4. 清晰的依赖结构**

```toml
[libraries]
# 定义库
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }

[plugins]
# 定义插件
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
room = { id = "androidx.room", version.ref = "room" }
```

将库依赖和插件分开定义，结构清晰。

**5. 多模块共享**

一旦在 toml 文件中定义了依赖版本，所有子模块都可以引用，无需重复定义版本号。

**6. 避免依赖扩散**

在没有版本目录的时代，如果 A 模块依赖 B 模块，B 模块依赖 C 库的 1.0 版本，A 模块又直接依赖 C 库的 2.0 版本，就会导致依赖冲突。使用版本目录可以统一管理，避免这种情况。

**Legado 项目中的实际使用示例：**

```toml
[versions]
coroutines = "1.10.2"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }

[bundles]
coroutines = ["kotlinx-coroutines-core", "kotlinx-coroutines-android"]
```

在 `app/build.gradle` 中使用：

```groovy
implementation(libs.bundles.coroutines)
```

---

### 题目 4：`api` 和 `implementation` 的区别是什么？

**答案：**

`api` 和 `implementation` 是 Gradle 中用于声明依赖的两种配置方式，它们的核心区别在于**依赖的可见性（传递性）**。

**1. 基本区别**

| 特性 | `implementation` | `api` |
|------|------------------|-------|
| 依赖方可用 | ❌ | ✅ |
| 传递性 | 不传递 | 传递 |
| 编译速度 | 更快 | 较慢 |
| 内部依赖变化影响 | 不影响依赖方 | 影响依赖方 |

**2. 具体解释**

假设有以下模块结构：

```
app 模块
├── 依赖 common 模块
└── 依赖 rhino 模块

rhino 模块
└── 依赖 okhttp（使用 api 或 implementation）
```

**使用 `implementation` 的情况：**

```groovy
// rhino/build.gradle
dependencies {
    implementation(libs.okhttp)  // okhttp 对 app 模块不可见
}
```

效果：
- rhino 模块内部可以使用 okhttp
- app 模块**不能**使用 okhttp
- 如果 rhino 升级或更换 okhttp 版本，app 模块**不需要**重新编译

**使用 `api` 的情况：**

```groovy
// rhino/build.gradle
dependencies {
    api(libs.okhttp)  // okhttp 对 app 模块可见
}
```

效果：
- rhino 模块内部可以使用 okhttp
- app 模块**可以**使用 okhttp
- 如果 rhino 升级或更换 okhttp 版本，app 模块**需要**重新编译

**3. 实际应用场景**

**使用 `implementation` 的场景：**
- 内部实现细节，不希望暴露给使用方
- 减少编译时间
- 降低模块间的耦合度

例如：

```groovy
// modules/rhino/build.gradle
dependencies {
    // 内部使用，不需要暴露给 app
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
}
```

**使用 `api` 的场景：**
- 公共 API，需要让使用方直接使用
- 典型的库模块

例如：

```groovy
// modules/rhino/build.gradle
dependencies {
    // 需要暴露给使用方（app 模块可能需要直接使用 Rhino 引擎）
    api(libs.mozilla.rhino)
}
```

**4. 为什么 Legado 这样设计？**

在 Legado 项目中：

```groovy
// app/build.gradle
dependencies {
    implementation(project(path: ':modules:rhino'))
}
```

app 模块可以直接使用 rhino 模块的功能。如果 rhino 使用 `api` 声明了某个依赖，app 就能使用该依赖；如果使用 `implementation`，app 就不能直接使用。

**5. 与旧版 `compile` 的关系**

`api` 类似于旧版 Gradle 中的 `compile` 配置，但由于 `compile` 会导致大量不必要的依赖传递，Gradle 3.0 之后被弃用，用 `implementation` 和 `api` 取而代之。

---

### 题目 5：如何创建一个新的 library 模块？

**答案：**

创建一个新的 library 模块需要以下步骤：

**步骤 1：在 settings.gradle 中注册模块**

```groovy
// settings.gradle
include ':app'
include ':modules:book'
include ':modules:rhino'
include ':modules:newmodule'  // 添加新模块
```

**步骤 2：创建模块目录结构**

```
项目根目录/
├── modules/
│   └── newmodule/           # 新模块目录
│       ├── build.gradle     # 模块构建配置
│       ├── proguard-rules.pro
│       └── src/
│           └── main/
│               ├── AndroidManifest.xml
│               ├── java/
│               │   └── com/
│               │       └── example/
│               │           └── newmodule/
│               └── res/
```

**步骤 3：创建 build.gradle 配置文件**

```groovy
// modules/newmodule/build.gradle

plugins {
    // 使用 alias 引用 toml 中定义的插件
    alias libs.plugins.android.library
    alias libs.plugins.kotlin.android
}

android {
    // 使用根项目定义的 compileSdk 版本
    compileSdk = compile_sdk_version
    
    // 模块的包名
    namespace 'com.example.newmodule'
    
    kotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
    
    defaultConfig {
        minSdk 21
        targetSdk 35
        
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles "consumer-rules.pro"
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    
    lint {
        checkDependencies true
    }
}

dependencies {
    // 使用 implementation，仅模块内部可见
    implementation(libs.androidx.annotation)
    implementation(libs.kotlin.stdlib)
    
    // 如果需要传递给 app 模块，使用 api
    // api(libs.xxx)
}
```

**步骤 4：创建 AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <!-- library 模块通常不需要 application 标签 -->
    
</manifest>
```

**步骤 5：在 app 模块中添加依赖**

```groovy
// app/build.gradle
dependencies {
    // 引用本地模块
    implementation(project(path: ':modules:newmodule'))
    
    // 现有依赖
    implementation(project(path: ':modules:book'))
    implementation(project(path: ':modules:rhino'))
}
```

**步骤 6：创建示例代码**

```kotlin
// modules/newmodule/src/main/java/com/example/newmodule/NewModule.kt
package com.example.newmodule

class NewModule {
    
    fun doSomething(): String {
        return "Hello from new module!"
    }
}
```

**步骤 7：同步 Gradle 并验证**

在 Android Studio 中点击 "Sync Now" 或执行：

```bash
./gradlew :modules:newmodule:build
```

**常见问题处理：**

1. **模块找不到？**
   - 确认 settings.gradle 中已正确 include
   - 执行 File → Sync Project with Gradle Files

2. **命名冲突？**
   - 确保 namespace 与其他模块不同
   - 避免与已发布的库包名冲突

3. **依赖冲突？**
   - 使用 `implementation` 而非 `api` 来隐藏内部依赖
   - 在 app 模块中使用 `constraints` 强制版本

---

### 题目 6：app 模块如何引用 rhino 模块？

**答案：**

app 模块引用 rhino 模块有以下几个步骤：

**方式一：通过 project 路径引用（推荐）**

```groovy
// app/build.gradle
dependencies {
    // 使用 project() 函数引用本地模块
    implementation(project(path: ':modules:rhino'))
    
    // 也可以使用字符串形式
    // implementation project(':modules:rhino')
}
```

**完整示例 - app/build.gradle 中的依赖声明：**

```groovy
dependencies {
    // ... 其他依赖
    
    // 引用 rhino 脚本引擎模块
    implementation(project(path: ':modules:rhino'))
    
    // 引用 book 书籍解析模块
    implementation(project(path: ':modules:book'))
}
```

**方式二：通过模块名称引用**

Gradle 会自动将 `modules:rhino` 解析为 `:modules:rhino`，所以也可以写成：

```groovy
implementation(project(':modules:rhino'))
```

**方式三：使用 includeBuild（复合构建）**

如果 rhino 是独立的 Gradle 项目（有自己的 build.gradle），可以使用：

```groovy
// settings.gradle
includeBuild('modules/rhino')  // 复合构建

// app/build.gradle
implementation(project(':rhino'))
```

但 Legado 项目没有使用这种方式，因为 rhino 不是一个独立的 Gradle 项目，而是项目内部的一个模块。

**引用后的使用：**

引用 rhino 模块后，app 模块就可以使用 Rhino 引擎了：

```kotlin
// 在 app 模块的代码中
import com.script.RhinoEngine  // rhino 模块的包名

// 使用 Rhino 引擎执行 JavaScript
val engine = RhinoEngine()
val result = engine.eval("1 + 1")  // 结果为 2
```

**传递依赖的处理：**

如果 rhino 模块使用 `api` 声明了某个依赖，那么 app 模块也可以直接使用该依赖：

```groovy
// modules/rhino/build.gradle
dependencies {
    // 暴露给依赖方
    api(libs.mozilla.rhino)
    
    // 内部使用，不暴露
    implementation(libs.kotlinx.coroutines.core)
}
```

这样 app 模块就可以直接使用 `org.mozilla:rhino` 库了。

**模块引用注意事项：**

1. **路径必须正确**：`modules:rhino` 表示目录结构为 `modules/rhino/`
2. **同步 Gradle**：修改后需要同步项目
3. **构建顺序**：Gradle 会自动处理模块间的构建顺序
4. **循环依赖**：避免模块间循环依赖（如 A→B→A）

---

## 九、验证题目答案

### 验证 1：项目有几个模块？

**答案：** 3 个模块（app、modules:book、modules:rhino）

### 验证 2：模块间依赖如何配置？

**答案：**

在 app/build.gradle 中使用 `implementation(project(path: ':modules:xxx'))` 配置：

```groovy
dependencies {
    implementation(project(path: ':modules:book'))
    implementation(project(path: ':modules:rhino'))
}
```

在子模块中使用 `api` 或 `implementation` 配置依赖。

### 验证 3：依赖版本在哪里统一管理？

**答案：**

在 `gradle/libs.versions.toml` 文件中统一管理：

```toml
[versions]
# 定义版本号
kotlin = "2.1.21"

[libraries]
# 定义依赖，使用 version.ref 引用版本
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
```

### 验证 4：library 模块与 app 模块的核心区别是什么？

**答案：**

| 区别 | app 模块 | library 模块 |
|------|----------|--------------|
| 插件 | `com.android.application` | `com.android.library` |
| 输出 | APK（可安装） | AAR（库文件） |
| AndroidManifest | 完整（有 application） | 简化（无 application） |
| 可以独立运行 | ✅ | ❌ |
| 可以被其他模块引用 | ❌ | ✅ |

核心区别：
- app 模块生成可安装的 APK
- library 模块生成 AAR 库文件，供其他模块使用

### 验证 5：api 和 implementation 有什么区别？

**答案：** 见题目 4 的详细答案。

---

## 十、参考资源

### 9.1 关键文件索引

| 文件 | 说明 |
|------|------|
| `settings.gradle` | 模块注册 |
| `build.gradle` | 根项目配置 |
| `gradle/libs.versions.toml` | 依赖版本管理 |
| `app/build.gradle` | 主应用配置 |
| `modules/book/build.gradle` | 书籍模块配置 |
| `modules/rhino/build.gradle` | 脚本引擎配置 |

### 9.2 相关知识点

学习完本章节后，建议继续学习：

1. **依赖注入** - 知识点 5
2. **Base 类封装** - 知识点 3
3. **Kotlin 协程** - 知识点 26

---

> 文档生成时间：2026年
> 基于 Legado 项目源码
