FROM ubuntu:24.04@sha256:4fbb8e6a8395de5a7550b33509421a2bafbc0aab6c06ba2cef9ebffbc7092d90 AS image-builder

ARG LIBHEIF_VERSION=1.23.1
ARG LIBHEIF_SHA256=0de0327f60fcd47de90d5654c6fe152232738d60d84fe084ec3e0f35e03b166a
ARG IMAGEMAGICK_VERSION=7.1.2-29
ARG IMAGEMAGICK_SHA256=4b131411aa77b051908ea35df65ca2c52dbe04b48c3ef6b06f35c1bb595b212f

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install --yes --no-install-recommends \
        build-essential \
        ca-certificates \
        cmake \
        curl \
        libde265-dev \
        libjpeg-dev \
        libpng-dev \
        libx265-dev \
        ninja-build \
        pkg-config \
        xz-utils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build

RUN curl --fail --location --proto '=https' --tlsv1.2 \
        --output libheif.tar.gz \
        "https://github.com/strukturag/libheif/releases/download/v${LIBHEIF_VERSION}/libheif-${LIBHEIF_VERSION}.tar.gz" \
    && echo "${LIBHEIF_SHA256}  libheif.tar.gz" | sha256sum --check --strict \
    && mkdir libheif-source \
    && tar --extract --gzip --file libheif.tar.gz --directory libheif-source --strip-components=1 \
    && cmake -S libheif-source -B libheif-build -G Ninja \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_INSTALL_PREFIX=/usr/local \
        -DBUILD_SHARED_LIBS=ON \
        -DBUILD_TESTING=OFF \
        -DWITH_EXAMPLES=ON \
        -DWITH_LIBDE265=ON \
        -DWITH_X265=ON \
        -DWITH_AOM_DECODER=OFF \
        -DWITH_AOM_ENCODER=OFF \
    && cmake --build libheif-build --parallel "$(nproc)" \
    && cmake --install libheif-build

RUN curl --fail --location --proto '=https' --tlsv1.2 \
        --output imagemagick.tar.xz \
        "https://github.com/ImageMagick/ImageMagick/releases/download/${IMAGEMAGICK_VERSION}/ImageMagick-${IMAGEMAGICK_VERSION}.tar.xz" \
    && echo "${IMAGEMAGICK_SHA256}  imagemagick.tar.xz" | sha256sum --check --strict \
    && mkdir imagemagick-source \
    && tar --extract --xz --file imagemagick.tar.xz --directory imagemagick-source --strip-components=1 \
    && cd imagemagick-source \
    && PKG_CONFIG_PATH=/usr/local/lib/pkgconfig ./configure \
        --prefix=/usr/local \
        --disable-static \
        --disable-docs \
        --without-perl \
        --without-x \
        --with-heic=yes \
        --with-jpeg=yes \
        --with-png=yes \
    && make -j"$(nproc)" \
    && make install \
    && ldconfig

FROM ubuntu:24.04@sha256:4fbb8e6a8395de5a7550b33509421a2bafbc0aab6c06ba2cef9ebffbc7092d90

ARG IMAGEMAGICK_VERSION=7.1.2-29
ARG LIBHEIF_VERSION=1.23.1

LABEL org.opencontainers.image.title="Diet Assistant Image Runtime" \
      org.opencontainers.image.description="Pinned ImageMagick and libheif runtime for product photo normalization and tests" \
      org.opencontainers.image.version="imagemagick-${IMAGEMAGICK_VERSION}_libheif-${LIBHEIF_VERSION}"

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install --yes --no-install-recommends \
        ca-certificates \
        curl \
        git \
        libde265-0 \
        libgomp1 \
        libjpeg-turbo8 \
        libpng16-16t64 \
        libx265-199 \
        openjdk-21-jdk-headless \
    && rm -rf /var/lib/apt/lists/*

COPY --from=image-builder /usr/local/ /usr/local/
COPY docker/image-runtime/policy.xml /etc/diet-assistant-imagemagick/policy.xml
COPY scripts/verify-image-runtime.sh /usr/local/bin/verify-image-runtime

ENV LD_LIBRARY_PATH=/usr/local/lib \
    MAGICK_CONFIGURE_PATH=/etc/diet-assistant-imagemagick \
    DIET_ASSISTANT_IMAGEMAGICK_EXECUTABLE=/usr/local/bin/magick

RUN ldconfig \
    && verify-image-runtime

WORKDIR /workspace
