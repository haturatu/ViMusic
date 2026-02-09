.PHONY: build release clean

NEWPIPEEXTRACTOR_REPO := https://github.com/TeamNewPipe/NewPipeExtractor
NEWPIPEEXTRACTOR_REF := v0.25.2
NEWPIPEEXTRACTOR_DIR := build/newpipeextractor
NEWPIPEEXTRACTOR_PATCH := patches/newpipeextractor.patch

build: $(NEWPIPEEXTRACTOR_DIR)
	NEWPIPE_EXTRACTOR_DIR=$(NEWPIPEEXTRACTOR_DIR) ./gradlew assembleDebug

release: $(NEWPIPEEXTRACTOR_DIR)
	NEWPIPE_EXTRACTOR_DIR=$(NEWPIPEEXTRACTOR_DIR) ./gradlew assembleRelease

$(NEWPIPEEXTRACTOR_DIR):
	mkdir -p build
	git clone --depth 1 --branch $(NEWPIPEEXTRACTOR_REF) $(NEWPIPEEXTRACTOR_REPO) $(NEWPIPEEXTRACTOR_DIR)
	@if [ -f "$(NEWPIPEEXTRACTOR_PATCH)" ]; then \
		git -C $(NEWPIPEEXTRACTOR_DIR) apply ../../$(NEWPIPEEXTRACTOR_PATCH); \
	fi

clean:
	rm -rf build
