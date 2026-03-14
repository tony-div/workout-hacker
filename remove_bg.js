const { Jimp } = require('jimp');
const path = require('path');

const INPUT = path.join(__dirname, 'src/assets/images/onboarding_ai_coach.png');
const OUTPUT = INPUT;

/**
 * Checkerboard colors detected during analysis:
 * 1. Near White: (~252, 252, 252)
 * 2. Medium Gray: (~202, 202, 202)
 */

const COLOR1 = { r: 252, g: 252, b: 252 };
const COLOR2 = { r: 202, g: 202, b: 202 };
const COLOR3 = { r: 0, g: 0, b: 0 }; // Legacy black background just in case

const TOLERANCE = 25; // Tolerance for color matching

function isMatch(r, g, b, target, tolerance) {
    return Math.abs(r - target.r) <= tolerance &&
        Math.abs(g - target.g) <= tolerance &&
        Math.abs(b - target.b) <= tolerance;
}

async function removeCheckerboard() {
    console.log('Reading image:', INPUT);
    const img = await Jimp.read(INPUT);
    const { width, height } = img.bitmap;

    console.log(`Processing ${width}x${height} image to remove checkerboard...`);

    let removedCount = 0;

    img.scan(0, 0, width, height, function (x, y, idx) {
        const r = this.bitmap.data[idx + 0];
        const g = this.bitmap.data[idx + 1];
        const b = this.bitmap.data[idx + 2];

        // Check against the two checkerboard colors and black
        if (isMatch(r, g, b, COLOR1, TOLERANCE) ||
            isMatch(r, g, b, COLOR2, TOLERANCE) ||
            isMatch(r, g, b, COLOR3, TOLERANCE)) {

            this.bitmap.data[idx + 3] = 0; // Set alpha to 0 (Transparent)
            removedCount++;
        }
    });

    console.log(`Removed ${removedCount} background pixels.`);
    console.log('Writing processed image back to:', OUTPUT);

    await img.write(OUTPUT);
    console.log('✅ Success! Checkerboard and black background removed.');
}

removeCheckerboard().catch(e => {
    console.error('❌ Failed to process image:', e);
    process.exit(1);
});
