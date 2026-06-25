/**
 * Navigation tree:
 *
 *  RootStack (native-stack)
 *   ├─ Tabs (bottom-tabs)          // Quotes | Portfolio, with header Logout
 *   │   ├─ Quotes
 *   │   └─ Portfolio
 *   └─ Trade                       // pushed from a quote row or the tab bar
 *
 * When there's no token the whole tree is replaced by the Auth screen (see App.tsx).
 */
import { CompositeScreenProps, NavigatorScreenParams } from '@react-navigation/native';
import {
  BottomTabScreenProps,
  createBottomTabNavigator,
} from '@react-navigation/bottom-tabs';
import {
  NativeStackScreenProps,
  createNativeStackNavigator,
} from '@react-navigation/native-stack';
import React from 'react';
import { Text, TouchableOpacity } from 'react-native';
import { useAuth } from './auth';
import PortfolioScreen from './screens/PortfolioScreen';
import QuotesScreen from './screens/QuotesScreen';
import TradeScreen from './screens/TradeScreen';
import { colors } from './theme';

/* --------------------------------------------------------- param lists */

export type TabParamList = {
  Quotes: undefined;
  Portfolio: undefined;
  TradeTab: { ticker?: string } | undefined;
};

export type RootStackParamList = {
  Tabs: NavigatorScreenParams<TabParamList>;
  Trade: { ticker?: string } | undefined;
};

/* ----------------------------------------------------- screen prop types */

/**
 * TradeScreen is mounted both as a root-stack screen ("Trade", pushed from a
 * quote row) and as a tab ("TradeTab"). Both pass a `{ ticker? }` param, so we
 * type the screen against a minimal shared shape rather than one navigator.
 */
export interface TradeScreenProps {
  route: { params?: { ticker?: string } };
}

export type QuotesTabScreenProps = CompositeScreenProps<
  BottomTabScreenProps<TabParamList, 'Quotes'>,
  NativeStackScreenProps<RootStackParamList>
>;

export type PortfolioTabScreenProps = CompositeScreenProps<
  BottomTabScreenProps<TabParamList, 'Portfolio'>,
  NativeStackScreenProps<RootStackParamList>
>;

/* -------------------------------------------------------------- shared UI */

function LogoutButton() {
  const { logout } = useAuth();
  return (
    <TouchableOpacity onPress={() => void logout()} accessibilityRole="button">
      <Text style={{ color: colors.primary, fontSize: 15, fontWeight: '600' }}>Logout</Text>
    </TouchableOpacity>
  );
}

/* ------------------------------------------------------------- navigators */

const Tab = createBottomTabNavigator<TabParamList>();
const Stack = createNativeStackNavigator<RootStackParamList>();

function Tabs() {
  return (
    <Tab.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: colors.card },
        headerTintColor: colors.text,
        headerTitleStyle: { fontWeight: '700' },
        headerRight: () => <LogoutButton />,
        headerRightContainerStyle: { paddingRight: 16 },
        tabBarStyle: { backgroundColor: colors.card, borderTopColor: colors.border },
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textDim,
      }}
    >
      <Tab.Screen
        name="Quotes"
        component={QuotesScreen}
        options={{ tabBarIcon: () => <Text>📈</Text> }}
      />
      <Tab.Screen
        name="Portfolio"
        component={PortfolioScreen}
        options={{ tabBarIcon: () => <Text>💼</Text> }}
      />
      <Tab.Screen
        name="TradeTab"
        component={TradeScreen}
        options={{ title: 'Trade', tabBarIcon: () => <Text>💱</Text> }}
      />
    </Tab.Navigator>
  );
}

export default function RootNavigator() {
  return (
    <Stack.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: colors.card },
        headerTintColor: colors.text,
        headerTitleStyle: { fontWeight: '700' },
        contentStyle: { backgroundColor: colors.bg },
      }}
    >
      <Stack.Screen name="Tabs" component={Tabs} options={{ headerShown: false }} />
      <Stack.Screen
        name="Trade"
        component={TradeScreen}
        options={({ route }) => ({
          title: route.params?.ticker ? `Trade ${route.params.ticker}` : 'Trade',
        })}
      />
    </Stack.Navigator>
  );
}
