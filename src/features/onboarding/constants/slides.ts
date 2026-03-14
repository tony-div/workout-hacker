export interface OnboardingSlideData {
  id: string;
  title: string;
  description: string;
  image: any;
  imageAlt: string;
}

export const ONBOARDING_SLIDES: OnboardingSlideData[] = [
  {
    id: 'ai-coach',
    title: 'Your Personal AI Coach',
    description: 'Real-time feedback , Rep counting\nand Form correction',
    image: require('../../../assets/images/onboarding_ai_coach.png'),
    imageAlt: 'Person running on treadmill with AI coach interface',
  },
  {
    id: 'train-smarter',
    title: 'Train Smarter',
    description: 'Fatigue detection , Form quality\nscore and Personalized workouts',
    image: require('../../../assets/images/onboarding_train_smarter.png'),
    imageAlt: 'Multiple athletes training in a gym',
  },
  {
    id: 'data-control',
    title: 'Your Data, Your Control',
    description: 'Local data processing , Camera is\noptional and No forced cloud',
    image: require('../../../assets/images/onboarding_data_control.png'),
    imageAlt: 'Person running outdoors with health stats on phone',
  },
  {
    id: 'get-started',
    title: 'Ready to start your journey',
    description: '',
    image: require('../../../assets/images/onboarding_get_started.png'),
    imageAlt: 'Group of athletes ready to start training',
  },
];

export const TOTAL_SLIDES = ONBOARDING_SLIDES.length;
export const LAST_SLIDE_INDEX = TOTAL_SLIDES - 1;