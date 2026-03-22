package com.firstapi.backend.service;

import com.firstapi.backend.common.CurrentSessionHolder;
import com.firstapi.backend.model.DashboardData;
import com.firstapi.backend.model.DashboardData.ModelShare;
import com.firstapi.backend.model.DashboardData.StatCard;
import com.firstapi.backend.model.DashboardData.TrendPoint;
import com.firstapi.backend.repository.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class DashboardService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final GroupRepository groupRepository;
    private final MyApiKeysRepository myApiKeysRepository;
    private final IpRepository ipRepository;
    private final AnnouncementRepository announcementRepository;
    private final RelayRecordRepository relayRecordRepository;

    public DashboardService(UserRepository userRepository,
                            AccountRepository accountRepository,
                            SubscriptionRepository subscriptionRepository,
                            GroupRepository groupRepository,
                            MyApiKeysRepository myApiKeysRepository,
                            IpRepository ipRepository,
                            AnnouncementRepository announcementRepository,
                            RelayRecordRepository relayRecordRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.groupRepository = groupRepository;
        this.myApiKeysRepository = myApiKeysRepository;
        this.ipRepository = ipRepository;
        this.announcementRepository = announcementRepository;
        this.relayRecordRepository = relayRecordRepository;
    }

    public DashboardData getDashboard() {
        int apiKeyCount = myApiKeysRepository.countByOwnerId(CurrentSessionHolder.require().getId());
        int accountCount = accountRepository.findAll().size();
        int userCount = userRepository.findAll().size();
        int subscriptionCount = subscriptionRepository.findAll().size();
        int groupCount = groupRepository.findAll().size();
        int ipCount = ipRepository.findAll().size();
        int announcementCount = announcementRepository.findAll().size();

        DashboardData data = new DashboardData();
        data.stats = List.of(
                new StatCard("API 密钥", String.valueOf(apiKeyCount), "全部密钥", "key", "#00f2ff"),
                new StatCard("活跃账户", String.valueOf(accountCount), "账户总数", "shield", "#3b82f6"),
                new StatCard("注册用户", String.valueOf(userCount), "用户总数", "users", "#8b5cf6"),
                new StatCard("订阅数量", String.valueOf(subscriptionCount), "订阅总数", "activity", "#10b981"),
                new StatCard("用户分组", String.valueOf(groupCount), "分组总数", "box", "#f59e0b"),
                new StatCard("IP 节点", String.valueOf(ipCount), "节点总数", "zap", "#00f2ff"),
                new StatCard("系统公告", String.valueOf(announcementCount), "公告总数", "database", "#ef4444")
        );

        // 模型分布（按调用次数百分比）
        List<RelayRecordRepository.ModelStat> modelStats = relayRecordRepository.groupByModel();
        long totalCalls = modelStats.stream().mapToLong(RelayRecordRepository.ModelStat::callCount).sum();
        String[] colors = {"#00f2ff", "#3b82f6", "#10b981", "#8b5cf6", "#f59e0b", "#ef4444", "#ec4899"};
        List<ModelShare> modelDistribution = new ArrayList<>();
        for (int i = 0; i < modelStats.size(); i++) {
            RelayRecordRepository.ModelStat ms = modelStats.get(i);
            int pct = totalCalls > 0 ? (int) Math.round(ms.callCount() * 100.0 / totalCalls) : 0;
            modelDistribution.add(new ModelShare(ms.modelName(), pct, colors[i % colors.length]));
        }
        data.modelDistribution = modelDistribution;

        // 趋势（最近 7 天）
        List<RelayRecordRepository.DayStat> dayStats = relayRecordRepository.groupByDate(7);
        List<TrendPoint> trends = new ArrayList<>();
        for (RelayRecordRepository.DayStat ds : dayStats) {
            trends.add(new TrendPoint(ds.date(), (int) Math.min(ds.totalTokens(), Integer.MAX_VALUE), (int) ds.callCount()));
        }
        data.trends = trends;

        data.alerts = Collections.emptyList();
        return data;
    }
}
