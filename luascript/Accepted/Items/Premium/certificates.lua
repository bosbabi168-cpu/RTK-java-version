kan_certificate = {
	use = async(function(player)
		local item = player:getInventoryItem(player.invSlot)

		local t = {graphic = item.icon, color = item.iconC}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0

		if player.actId == 0 then
			-- not registered
			player:dialogSeq(
				{
					t,
					"Karaktermu saat ini belum terdaftar pada sebuah akun. Ia harus terdaftar untuk memakai barang ini."
				},
				0
			)
			return
		end

		local amount = item.price

		if player:hasItem("kan_certificate_" .. amount, 1) ~= true then
			return
		end
		characterLog.kanCertificateAdd(player, item)
		KanNpc.addKan(player, amount)
		player:removeItem("kan_certificate_" .. amount, 1, 9)

		player:dialogSeq(
			{
				t,
				"Kau baru saja menambahkan " .. Tools.formatNumber(amount) .. " Kan ke akunmu."
			},
			0
		)
	end)
}

kan_certificate_500 = {
	use = function(player)
		kan_certificate.use(player)
	end
}
kan_certificate_1000 = {
	use = function(player)
		kan_certificate.use(player)
	end
}
kan_certificate_1500 = {
	use = function(player)
		kan_certificate.use(player)
	end
}
kan_certificate_2000 = {
	use = function(player)
		kan_certificate.use(player)
	end
}
kan_certificate_2500 = {
	use = function(player)
		kan_certificate.use(player)
	end
}
kan_certificate_3000 = {
	use = function(player)
		kan_certificate.use(player)
	end
}
kan_certificate_3500 = {
	use = function(player)
		kan_certificate.use(player)
	end
}
kan_certificate_4000 = {
	use = function(player)
		kan_certificate.use(player)
	end
}
kan_certificate_4500 = {
	use = function(player)
		kan_certificate.use(player)
	end
}
kan_certificate_5000 = {
	use = function(player)
		kan_certificate.use(player)
	end
}
