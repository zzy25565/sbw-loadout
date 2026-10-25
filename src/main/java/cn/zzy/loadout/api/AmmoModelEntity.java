package cn.zzy.loadout.api;

/** 弹体仅同步弹药 ID；客户端从已同步的数据定义加载模型，不接收任意资源路径。 */
public interface AmmoModelEntity {
  String loadoutAmmoModel();

  void loadoutAmmoModel(String id);
}
