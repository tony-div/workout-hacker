const { Jimp } = require('jimp');
const path = require('path');

const INPUT = path.join(__dirname, 'src/assets/images/onboarding_ai_coach.png');

async function analyze() {
    const img = await Jimp.read(INPUT);
    const { width, height } = img.bitmap;

    console.log(`Analyzing ${width}x${height} image...`);

    // Check some edge pixels to see if they are a checkerboard
    const samples = [];
    for (let y = 0; y < 20; y++) {
        let row = '';
        for (let x = 0; x < 20; x++) {
            const idx = (y * width + x) * 4;
            const r = img.bitmap.data[idx];
            const g = img.bitmap.data[idx + 1];
            const b = img.bitmap.data[idx + 2];
            const a = img.bitmap.data[idx + 3];
            row += `(${r},${g},${b},${a}) `;
        }
        samples.push(row);
    }

    console.log('Top-left 20x20 pixel data:');
    samples.forEach(s => console.log(s));
}

analyze().catch(console.error);
